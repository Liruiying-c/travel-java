package org.example.traveljava.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.example.traveljava.support.FakeLlmServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.traveljava.support.FakeLlmServer.chatCompletion;
import static org.example.traveljava.support.FakeLlmServer.chunkLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用假的 HTTP 服务验证 OkHttp 实现：正常路径 + 各种异常分支。
 *
 * <p>这些用例的价值在于：它们覆盖的正是"AI 生成代码最容易忽略、线上最容易出事"的地方。</p>
 */
class OkHttpLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmProperties properties(String baseUrl) {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-api-key");
        properties.setModel("test-model");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setReadTimeout(Duration.ofMillis(800));
        properties.setStreamReadTimeout(Duration.ofMillis(800));
        return properties;
    }

    private OkHttpLlmClient client(LlmProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build();
        return new OkHttpLlmClient(httpClient, properties, objectMapper);
    }

    // ---------- 非流式 ----------

    @Test
    @DisplayName("正常返回：取出 content，并保留原始响应")
    void chat_shouldExtractContent() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, chatCompletion("北京三日游攻略")))) {
            LlmResult result = client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京")));

            assertEquals("北京三日游攻略", result.content());
            assertEquals("test-model", result.model());
            assertTrue(result.rawResponse().contains("choices"));
            assertTrue(result.latencyMs() >= 0);
        }
    }

    @Test
    @DisplayName("用户输入带引号和换行时，请求体依然是合法 JSON（原实现手工拼字符串会在这里翻车）")
    void chat_shouldEscapeUserContent() throws Exception {
        String tricky = "帮我规划\"北京\"之行\n第二天{可选}行程";
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, chatCompletion("ok")))) {
            client(properties(server.baseUrl())).chat(List.of(
                    ChatMessage.system("你是助手，注意\"专业\"与\n简洁"),
                    ChatMessage.user(tricky)));

            JsonNode requestJson = objectMapper.readTree(server.lastRequestBody());
            assertEquals(2, requestJson.path("messages").size());
            assertEquals("你是助手，注意\"专业\"与\n简洁",
                    requestJson.path("messages").get(0).path("content").asText());
            assertEquals(tricky, requestJson.path("messages").get(1).path("content").asText());
            assertFalse(requestJson.path("stream").asBoolean());
        }
    }

    @Test
    @DisplayName("服务商专有参数会并入请求体，可用配置关掉模型的思考路径")
    void chat_shouldMergeExtraParamsIntoRequestBody() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, chatCompletion("ok")))) {
            LlmProperties properties = properties(server.baseUrl());
            properties.setExtraParams(Map.of("enable_thinking", false));

            client(properties).chat(List.of(ChatMessage.user("北京")));

            JsonNode requestJson = objectMapper.readTree(server.lastRequestBody());
            assertFalse(requestJson.path("enable_thinking").asBoolean(true));
            assertEquals("test-model", requestJson.path("model").asText());
            assertEquals(0.7, requestJson.path("temperature").asDouble());
        }
    }

    @Test
    @DisplayName("HTTP 500：分类为 HTTP_ERROR 且可重试")
    void chat_shouldFailOnServerError() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 500, "{\"error\":\"internal\"}"))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.HTTP_ERROR, exception.getKind());
            assertEquals(500, exception.getStatusCode());
            assertTrue(exception.isRetryable());
        }
    }

    @Test
    @DisplayName("HTTP 401：分类为 HTTP_ERROR 但不应重试")
    void chat_shouldNotRetryOnUnauthorized() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 401, "{\"error\":\"bad key\"}"))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.HTTP_ERROR, exception.getKind());
            assertFalse(exception.isRetryable());
        }
    }

    @Test
    @DisplayName("网关返回 HTML 错误页：分类为 PARSE_ERROR，而不是让人摸不着头脑的空指针")
    void chat_shouldFailOnHtmlBody() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, "<html><body>502 Bad Gateway</body></html>"))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.PARSE_ERROR, exception.getKind());
        }
    }

    @Test
    @DisplayName("choices 为空：分类为 EMPTY_RESPONSE")
    void chat_shouldFailOnEmptyChoices() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, "{\"choices\":[]}"))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.EMPTY_RESPONSE, exception.getKind());
        }
    }

    @Test
    @DisplayName("响应体为空：分类为 EMPTY_RESPONSE")
    void chat_shouldFailOnEmptyBody() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.empty(exchange, 200))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.EMPTY_RESPONSE, exception.getKind());
        }
    }

    @Test
    @DisplayName("模型迟迟不回：分类为 TIMEOUT 且可重试")
    void chat_shouldTimeout() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(exchange -> Thread.sleep(3000))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.TIMEOUT, exception.getKind());
            assertTrue(exception.isRetryable());
        }
    }

    @Test
    @DisplayName("没配密钥时给出明确的配置错误，而不是发一个必然 401 的请求")
    void chat_shouldFailFastWhenApiKeyMissing() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 200, chatCompletion("ok")))) {
            LlmProperties properties = properties(server.baseUrl());
            properties.setApiKey("");

            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties).chat(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.CONFIG_ERROR, exception.getKind());
            assertEquals(0, server.requestCount());
        }
    }

    // ---------- 流式 ----------

    @Test
    @DisplayName("流式正常：分片按顺序回调，结束时给出完整文本")
    void chatStream_shouldDeliverDeltasInOrder() throws Exception {
        List<String> lines = List.of(
                chunkLine("北京"), "", chunkLine("欢迎"), "", chunkLine("你"), "", "data: [DONE]", "");
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.sse(exchange, lines))) {
            RecordingListener listener = new RecordingListener();

            client(properties(server.baseUrl())).chatStream(List.of(ChatMessage.user("北京")), listener);

            assertEquals(List.of("北京", "欢迎", "你"), listener.deltas);
            assertEquals(1, listener.completed.get());
            assertEquals(0, listener.failed.get());
            assertEquals("北京欢迎你", listener.fullText);
        }
    }

    @Test
    @DisplayName("流式：遇到无法解析的分片直接跳过，不影响后续分片")
    void chatStream_shouldSkipMalformedChunk() throws Exception {
        List<String> lines = List.of(
                chunkLine("北京"), "", "data: {不是合法JSON}", "", chunkLine("欢迎"), "", "data: [DONE]", "");
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.sse(exchange, lines))) {
            RecordingListener listener = new RecordingListener();

            client(properties(server.baseUrl())).chatStream(List.of(ChatMessage.user("北京")), listener);

            assertEquals(List.of("北京", "欢迎"), listener.deltas);
            assertEquals(1, listener.completed.get());
        }
    }

    @Test
    @DisplayName("流式：没有收到 [DONE] 就断开时，仍以已收到的内容正常收尾")
    void chatStream_shouldCompleteWhenStreamEndsEarly() throws Exception {
        List<String> lines = List.of(chunkLine("北京"), "", chunkLine("欢迎"), "");
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.sse(exchange, lines))) {
            RecordingListener listener = new RecordingListener();

            client(properties(server.baseUrl())).chatStream(List.of(ChatMessage.user("北京")), listener);

            assertEquals(1, listener.completed.get());
            assertEquals(0, listener.failed.get());
            assertEquals("北京欢迎", listener.fullText);
        }
    }

    @Test
    @DisplayName("流式：接口报错时只回调 onError，不回调 onComplete")
    void chatStream_shouldReportError() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 500, "{\"error\":\"boom\"}"))) {
            RecordingListener listener = new RecordingListener();

            client(properties(server.baseUrl())).chatStream(List.of(ChatMessage.user("北京")), listener);

            assertEquals(0, listener.completed.get());
            assertEquals(1, listener.failed.get());
            assertNotNull(listener.error);
            assertTrue(listener.error instanceof LlmException);
            assertEquals(LlmErrorKind.HTTP_ERROR, ((LlmException) listener.error).getKind());
        }
    }

    @Test
    @DisplayName("流式：下游要求停止时立即中断读取，不再消耗后面的 token")
    void chatStream_shouldStopWhenListenerRejects() throws Exception {
        List<String> lines = List.of(
                chunkLine("一"), "", chunkLine("二"), "", chunkLine("三"), "", chunkLine("四"), "",
                "data: [DONE]", "");
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.sse(exchange, lines))) {
            RecordingListener listener = new RecordingListener();
            listener.stopAfter = 2;

            client(properties(server.baseUrl())).chatStream(List.of(ChatMessage.user("北京")), listener);

            assertEquals(List.of("一", "二"), listener.deltas);
            assertEquals(1, listener.completed.get());
        }
    }

    // ---------- 内部流式（对外一次性返回） ----------

    @Test
    @DisplayName("内部流式：把多个分片拼成完整结果返回，长耗时生成不再受空闲读超时影响")
    void chatStreaming_shouldAssembleChunksIntoOneResult() throws Exception {
        List<String> lines = List.of(
                chunkLine("{\"city\":\""), "", chunkLine("北京"), "", chunkLine("\"}"), "",
                "data: [DONE]", "");
        try (FakeLlmServer server = new FakeLlmServer(exchange -> FakeLlmServer.sse(exchange, lines))) {
            LlmResult result = client(properties(server.baseUrl()))
                    .chatStreaming(List.of(ChatMessage.user("北京")));

            assertEquals("{\"city\":\"北京\"}", result.content());
            assertEquals("test-model", result.model());
            assertEquals("{\"city\":\"北京\"}", result.rawResponse());
            assertTrue(result.latencyMs() >= 0);
        }
    }

    @Test
    @DisplayName("内部流式：接口报错时抛 LlmException，并保留失败分类")
    void chatStreaming_shouldThrowOnHttpError() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.json(exchange, 500, "{\"error\":\"boom\"}"))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chatStreaming(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.HTTP_ERROR, exception.getKind());
            assertTrue(exception.isRetryable());
        }
    }

    @Test
    @DisplayName("内部流式：模型一个字都没返回时，抛 EMPTY_RESPONSE 而不是返回空字符串")
    void chatStreaming_shouldFailWhenNoContent() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer(
                exchange -> FakeLlmServer.sse(exchange, List.of("data: [DONE]", "")))) {
            LlmException exception = assertThrows(LlmException.class,
                    () -> client(properties(server.baseUrl())).chatStreaming(List.of(ChatMessage.user("北京"))));

            assertEquals(LlmErrorKind.EMPTY_RESPONSE, exception.getKind());
        }
    }

    private static class RecordingListener implements LlmStreamListener {

        private final List<String> deltas = new ArrayList<>();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private int stopAfter = Integer.MAX_VALUE;

        private String fullText;
        private Throwable error;

        @Override
        public boolean onDelta(String delta) {
            deltas.add(delta);
            return deltas.size() < stopAfter;
        }

        @Override
        public void onComplete(String fullText) {
            completed.incrementAndGet();
            this.fullText = fullText;
        }

        @Override
        public void onError(Throwable error) {
            failed.incrementAndGet();
            this.error = error;
        }
    }
}
