package org.example.traveljava.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.vo.TravelRecommendVO;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型返回的自然语言响应转换成 {@link TravelRecommendVO}。
 *
 * <p>从 Service 里抽出来的原因：这是一段纯函数逻辑（入参字符串、出参对象），
 * 没有任何 Spring 依赖，抽出来以后可以单独写用例把它彻底测一遍——
 * 而它恰好是整个链路里最容易出问题的一环。</p>
 */
@Component
public class LlmResponseParser {

    /** 匹配 ```json ... ``` 或 ``` ... ``` 代码块，允许 json 后面没有换行、允许前后有空格 */
    private static final Pattern FENCED_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final ObjectMapper objectMapper;

    public LlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TravelRecommendVO parseTravelResponse(String rawResponse) {
        String json = extractJson(rawResponse);
        if (json == null) {
            return failure(rawResponse, "未能从模型响应中提取 JSON");
        }
        TravelRecommendVO parsed;
        try {
            parsed = objectMapper.readValue(json, TravelRecommendVO.class);
        } catch (Exception e) {
            return failure(rawResponse, "模型响应 JSON 解析失败");
        }
        if (parsed == null) {
            return failure(rawResponse, "模型响应 JSON 解析失败");
        }
        if (parsed.getDailyItinerary() == null || parsed.getDailyItinerary().isEmpty()) {
            return failure(rawResponse, "模型未返回有效的行程安排，请重试");
        }
        // 解析成功就明确置为 true：不再依赖模型自己在 JSON 里回填 success 字段
        parsed.setSuccess(true);
        parsed.setError(null);
        parsed.setRawResponse(rawResponse);
        return parsed;
    }

    /**
     * 从模型输出里提取 JSON 对象字符串，提取不到返回 null。
     *
     * <p>三级降级：带 json 标记的代码块 → 裸 JSON → 首个 { 到最后一个 } 之间的内容。</p>
     */
    public String extractJson(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = FENCED_BLOCK.matcher(trimmed);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (isValidJson(candidate)) {
                return candidate;
            }
        }
        if (isValidJson(trimmed)) {
            return trimmed;
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = trimmed.substring(start, end + 1);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isValidJson(String candidate) {
        if (candidate == null || !candidate.startsWith("{") || !candidate.endsWith("}")) {
            return false;
        }
        try {
            objectMapper.readTree(candidate);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private TravelRecommendVO failure(String rawResponse, String message) {
        TravelRecommendVO result = new TravelRecommendVO();
        result.setSuccess(false);
        result.setError(message);
        result.setRawResponse(rawResponse);
        return result;
    }
}
