package org.example.traveljava.utils;

import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LLMUtils {
    private String apiKey;
    private String baseUrl;
    private String model;
    private OkHttpClient client;
    private ObjectMapper objectMapper = new ObjectMapper();

    public LLMUtils(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;

        // 创建网络连接+配置请求参数
        this.client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    // 调用大模型接口 旅行推荐接口
    public String chat(String systemPrompt, String userPrompt) throws IOException {
        // 构建请求体
        String requestBody = buildRequestBody(systemPrompt, userPrompt, false);
//        System.out.println(requestBody);
        // 创建一个post请求
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), requestBody))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM调用异常：" + response.code());
            }
//            System.out.println(response.body().string());
            String responseBody = response.body().string();

            return extractContent(responseBody);
        }catch (IOException e){
            throw new RuntimeException(e);
        }

    }

    public String buildRequestBody(String systemPrompt, String userPrompt, Boolean stream) {
        StringBuilder str = new StringBuilder();
        str.append("{");
        str.append("\"model\":\""+model+"\",");
        str.append("\"stream\":" + stream + ",");
        str.append("\"messages\":[");
        if(systemPrompt != null && !systemPrompt.isEmpty()) {
            str.append("{\"role\":\"system\",\"content\":\"" + systemPrompt + "\"},");
        }
        str.append("{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}");
        str.append("],");
        str.append("\"temperature\":0.7");
        str.append("}");
        return str.toString();
    }


    // JSON字符串转义方法
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")      // 转义反斜杠
                .replace("\"", "\\\"")      // 转义双引号
                .replace("\n", "\\n")       // 转义换行符
                .replace("\r", "\\r")       // 转义回车符
                .replace("\t", "\\t");      // 转义制表符
    }


    // 模型返回数据处理
    private String extractContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        return "";
    }

    public String chatStream(String systemPrompt, String userPrompt, Consumer<String> callback) throws IOException { // 流式返回，需定义回调方法
        String requestBody = buildRequestBody(systemPrompt, userPrompt, true);
        // 创建一个post请求
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), requestBody))
                .build();

        // 记录完整内容
        StringBuilder fullContent = new StringBuilder();

        try (Response response = client.newCall(request).execute()){
            if(!response.isSuccessful()) {
                throw new IOException("LLM调用异常：" + response.code());
            }
            // response.body().string(); // 二进制流数据
            try(BufferedReader reader = new BufferedReader((new InputStreamReader(response.body().byteStream(),java.nio.charset.StandardCharsets.UTF_8)))) { // 读取流数据
                String line;
                while ((line = reader.readLine()) != null) {
                    if(line.startsWith("data: ")) {
                        // 截取字符串
                        String data = line.substring(6);
                        if("[DONE]".equals(data)) {
                            break;
                        }
                        String content = parseStreamContent(data);
                        if(content != null && !content.isEmpty()) {
                            fullContent.append(content);
                            if(callback != null) {
                                callback.accept(content);
                            }
                        }
                    }
                }
            }
        }catch (Exception e) {
            e.printStackTrace(); // 打印详细日志，方便看是超时还是解析错
            throw new RuntimeException(e);
        }

        return fullContent.toString();
    }

    // 大模型返回对话流数据处理函数
    private String parseStreamContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                return delta.path("content").asText("");
            }
        } catch (Exception e) {
            System.out.println("解析流式数据失败: {}" + e.getMessage());
        }
        return null;
    }

}
