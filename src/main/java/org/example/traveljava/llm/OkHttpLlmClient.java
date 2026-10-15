package org.example.traveljava.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 OkHttp 的实现，适配 OpenAI 兼容协议的 /chat/completions 接口。
 *
 * <p>与原 LLMutils 的区别：</p>
 * <ul>
 *   <li>请求体交给 Jackson 序列化，不再手工拼字符串
 *       （原实现里 systemPrompt 是直接拼进 JSON 的，没做转义，一旦提示词里带引号或换行，请求体就不是合法 JSON）；</li>
 *   <li>OkHttpClient、超时时间都由外部注入，可以在测试里换成假服务器和 300 毫秒超时；</li>
 *   <li>失败统一抛 {@link LlmException} 并带上分类，流式方法则通过回调上报错误。</li>
 * </ul>
 */
public class OkHttpLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OkHttpLlmClient.class);

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json;charset=utf-8");
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";
    private static final int ABBREVIATE_LIMIT = 300;

    private final OkHttpClient httpClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public OkHttpLlmClient(OkHttpClient httpClient, LlmProperties properties, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return properties.getBaseUrl();
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public LlmResult chat(List<ChatMessage> messages) {
        long startedAt = System.currentTimeMillis();
        Request request = buildRequest(messages, false);
        String rawResponse;
        try (Response response = httpClient.newCall(request).execute()) {
            rawResponse = ensureSuccess(response);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw classify(e);
        }
        String content = extractContent(rawResponse);
        return new LlmResult(content, rawResponse, properties.getModel(), System.currentTimeMillis() - startedAt);
    }

    @Override
    public void chatStream(List<ChatMessage> messages, LlmStreamListener listener) {
        StringBuilder fullText = new StringBuilder();
        boolean receivedDone = false;
        try {
            Request request = buildRequest(messages, true);
            OkHttpClient streamingClient = httpClient.newBuilder()
                    .readTimeout(properties.getStreamReadTimeout())
                    .build();

            try (Response response = streamingClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful()) {
                    String errorBody = body == null ? "" : body.string();
                    throw new LlmException(LlmErrorKind.HTTP_ERROR,
                            "大模型接口返回 HTTP " + response.code() + "：" + abbreviate(errorBody),
                            response.code(), null);
                }
                if (body == null) {
                    throw new LlmException(LlmErrorKind.EMPTY_RESPONSE, "大模型接口返回空响应体", response.code(), null);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String payload = toSsePayload(line);
                        if (payload == null || payload.isEmpty()) {
                            continue;
                        }
                        if (SSE_DONE.equals(payload)) {
                            receivedDone = true;
                            break;
                        }
                        String delta = extractDelta(payload);
                        if (delta.isEmpty()) {
                            continue;
                        }
                        fullText.append(delta);
                        if (!listener.onDelta(delta)) {
                            log.info("下游已断开，主动停止读取流式响应");
                            break;
                        }
                    }
                }
            }
            if (!receivedDone) {
                log.warn("流式响应未收到 [DONE] 标记，连接可能被提前中断，已拼接 {} 个字符", fullText.length());
            }
            listener.onComplete(fullText.toString());
        } catch (Exception e) {
            listener.onError(e instanceof LlmException llmException ? llmException : classify(e));
        }
    }

    @Override
    public LlmResult chatStreaming(List<ChatMessage> messages) {
        long startedAt = System.currentTimeMillis();
        StringBuilder content = new StringBuilder();
        AtomicReference<LlmException> failure = new AtomicReference<>();

        chatStream(messages, new LlmStreamListener() {
            @Override
            public boolean onDelta(String delta) {
                content.append(delta);
                return true;
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error instanceof LlmException llmException ? llmException : classify(error));
            }
        });

        if (failure.get() != null) {
            throw failure.get();
        }
        String text = content.toString();
        if (text.isBlank()) {
            throw new LlmException(LlmErrorKind.EMPTY_RESPONSE, "模型没有返回任何内容", null, null);
        }
        return new LlmResult(text, text, properties.getModel(), System.currentTimeMillis() - startedAt);
    }

    /**
     * 组装请求体。包级可见，方便单元测试直接验证转义行为。
     */
    String buildRequestBody(List<ChatMessage> messages, boolean stream) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("stream", stream);
        ArrayNode messageArray = root.putArray("messages");
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            item.put("content", message.content() == null ? "" : message.content());
        }
        root.put("temperature", properties.getTemperature());
        if (properties.getMaxTokens() != null) {
            root.put("max_tokens", properties.getMaxTokens());
        }
        // 服务商专有参数：若与上面的标准字段同名，会覆盖标准字段
        if (properties.getExtraParams() != null) {
            properties.getExtraParams().forEach((key, value) -> root.set(key, objectMapper.valueToTree(value)));
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException(LlmErrorKind.UNKNOWN, "构造请求体失败", null, e);
        }
    }

    private Request buildRequest(List<ChatMessage> messages, boolean stream) {
        requireConfigured();
        String body = buildRequestBody(messages, stream);
        Request.Builder builder = new Request.Builder()
                .url(properties.getBaseUrl() + CHAT_COMPLETIONS_PATH)
                .addHeader("Authorization", "Bearer " + properties.getApiKey())
                .post(RequestBody.create(body, JSON_MEDIA_TYPE));
        if (stream) {
            builder.addHeader("Accept", "text/event-stream");
        }
        return builder.build();
    }

    private void requireConfigured() {
        if (!properties.isApiKeyPresent()) {
            throw new LlmException(LlmErrorKind.CONFIG_ERROR,
                    "未配置 llm.api-key，请通过环境变量 LLM_API_KEY 注入", null, null);
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new LlmException(LlmErrorKind.CONFIG_ERROR, "未配置 llm.model", null, null);
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new LlmException(LlmErrorKind.CONFIG_ERROR, "未配置 llm.base-url", null, null);
        }
    }

    private String ensureSuccess(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        String body = responseBody == null ? "" : responseBody.string();
        if (!response.isSuccessful()) {
            throw new LlmException(LlmErrorKind.HTTP_ERROR,
                    "大模型接口返回 HTTP " + response.code() + "：" + abbreviate(body), response.code(), null);
        }
        if (body.isBlank()) {
            throw new LlmException(LlmErrorKind.EMPTY_RESPONSE, "大模型接口返回空响应体", response.code(), null);
        }
        return body;
    }

    private String extractContent(String rawResponse) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            throw new LlmException(LlmErrorKind.PARSE_ERROR,
                    "大模型返回内容不是合法 JSON：" + abbreviate(rawResponse), null, e);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmException(LlmErrorKind.EMPTY_RESPONSE,
                    "大模型返回的 choices 为空：" + abbreviate(rawResponse), null, null);
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new LlmException(LlmErrorKind.EMPTY_RESPONSE, "大模型返回的 content 为空", null, null);
        }
        return content;
    }

    private String extractDelta(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode choice = choices.get(0);
            JsonNode content = choice.path("delta").path("content");
            if (content.isMissingNode() || content.isNull()) {
                // 有的服务商最后一个分片返回 message 而不是 delta
                content = choice.path("message").path("content");
            }
            return content.asText("");
        } catch (Exception e) {
            log.debug("忽略无法解析的流式分片：{}", abbreviate(payload));
            return "";
        }
    }

    /** 从一行 SSE 文本里取出 data 字段，非数据行返回 null。 */
    private String toSsePayload(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(":") || !trimmed.startsWith(SSE_DATA_PREFIX)) {
            // 空行、以冒号开头的心跳注释、其他字段一律忽略
            return null;
        }
        return trimmed.substring(SSE_DATA_PREFIX.length()).trim();
    }

    private LlmException classify(Throwable e) {
        if (e instanceof LlmException llmException) {
            return llmException;
        }
        if (e instanceof SocketTimeoutException || e instanceof InterruptedIOException) {
            return new LlmException(LlmErrorKind.TIMEOUT,
                    "调用大模型超时（read-timeout=" + properties.getReadTimeout() + "）", null, e);
        }
        if (e instanceof UnknownHostException || e instanceof ConnectException) {
            return new LlmException(LlmErrorKind.NETWORK_ERROR, "无法连接大模型服务：" + e.getMessage(), null, e);
        }
        return new LlmException(LlmErrorKind.UNKNOWN, "调用大模型失败：" + e.getMessage(), null, e);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= ABBREVIATE_LIMIT ? oneLine : oneLine.substring(0, ABBREVIATE_LIMIT) + "...";
    }
}
