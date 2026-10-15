package org.example.traveljava.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个"假的大模型服务"，用 JDK 自带的 HttpServer 实现，不引入任何测试依赖。
 *
 * <p>为什么不用 Mockito 直接 mock HTTP 调用：真实的坑都在传输层——
 * 读超时、非 2xx、返回 HTML 错误页、SSE 分片、连接提前中断。
 * 这些用假的 HTTP 服务验证，才真的验证到了代码。</p>
 *
 * <p>它还记录请求体，可以用来断言"用户输入带引号/换行时请求体依然是合法 JSON"。</p>
 */
public class FakeLlmServer implements AutoCloseable {

    @FunctionalInterface
    public interface Responder {
        void respond(HttpExchange exchange) throws Exception;
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
    private final AtomicInteger requestCount = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    public FakeLlmServer(Responder responder) throws IOException {
        this.executor = Executors.newCachedThreadPool();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/v1/chat/completions", exchange -> handle(exchange, responder));
        this.server.setExecutor(executor);
        this.server.start();
    }

    /** 返回 http://127.0.0.1:port/v1 形式的地址，直接塞给 LlmProperties。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public String lastRequestBody() {
        return lastRequestBody.get();
    }

    public int requestCount() {
        return requestCount.get();
    }

    private void handle(HttpExchange exchange, Responder responder) {
        requestCount.incrementAndGet();
        try (InputStream in = exchange.getRequestBody()) {
            lastRequestBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 测试替身，忽略
        }
        try {
            responder.respond(exchange);
        } catch (Exception ignored) {
            // 测试替身不向上抛异常，避免干扰断言
        } finally {
            exchange.close();
        }
    }

    /** 返回一段普通响应。 */
    public static void json(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 返回空响应体。 */
    public static void empty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }

    /** 逐行返回 SSE 数据（0 表示 chunked 编码）。 */
    public static void sse(HttpExchange exchange, List<String> lines) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            for (String line : lines) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.write('\n');
                out.flush();
            }
        }
    }

    /** 拼一个 OpenAI 兼容协议的流式分片。 */
    public static String chunkLine(String content) {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.putObject("delta").put("content", content);
        return "data: " + write(root);
    }

    /** 拼一个非流式响应体。 */
    public static String chatCompletion(String content) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", "test-model");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.putObject("message").put("role", "assistant").put("content", content);
        return write(root);
    }

    private static String write(ObjectNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("测试替身构造 JSON 失败", e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
