package org.example.traveljava.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.vo.TravelRecommendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯函数测试：不联网、不起 Spring 容器，跑得飞快，覆盖的却是最容易出问题的一段逻辑。
 */
class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

    private static final String VALID_RESPONSE = """
            {
              "success": true,
              "city": "北京",
              "days": 2,
              "totalBudget": 5000,
              "dailyItinerary": [
                {
                  "day": 1,
                  "date": "第1天",
                  "morning": {"spot": "故宫", "duration": "3小时", "ticket": "60元", "transportation": "地铁", "description": "必看"},
                  "afternoon": {"spot": "景山公园", "duration": "1小时", "ticket": "2元", "transportation": "步行", "description": "俯瞰中轴"},
                  "evening": {"spot": "南锣鼓巷", "duration": "2小时", "ticket": "免费", "transportation": "地铁", "description": "小吃"}
                }
              ],
              "budgetBreakdown": {"accommodation": 1500, "food": 1200, "transportation": 800, "tickets": 1000, "other": 500},
              "tips": ["提前订票"],
              "warnings": ["注意保暖"]
            }
            """;

    static Stream<Arguments> extractJsonCases() {
        return Stream.of(
                Arguments.of("带 json 标记的代码块", "```json\n{\"a\":1}\n```", "{\"a\":1}"),
                Arguments.of("单行代码块（json 标记后没有换行）", "```json{\"a\":1}```", "{\"a\":1}"),
                Arguments.of("无标记代码块", "```\n{\"a\":1}\n```", "{\"a\":1}"),
                Arguments.of("裸 JSON", "{\"a\":1}", "{\"a\":1}"),
                Arguments.of("前后夹着解释文字", "好的，这是结果：\n{\"a\":1}\n希望有帮助", "{\"a\":1}"),
                Arguments.of("代码块之后还有带花括号的说明", "```json{\"a\":1}```\n提示：注意{天气}变化", "{\"a\":1}"),
                Arguments.of("字符串值里含花括号", "{\"a\":\"{x}\"}", "{\"a\":\"{x}\"}"),
                Arguments.of("前后有空白", "  \n{\"a\":1}\n  ", "{\"a\":1}")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("extractJsonCases")
    @DisplayName("extractJson 能从各种包裹形式里取出 JSON")
    void extractJson_shouldExtractJsonFromWrappers(String description, String raw, String expected) {
        assertEquals(expected, parser.extractJson(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "抱歉，我无法完成这个请求"})
    @DisplayName("没有 JSON 时返回 null，而不是抛出异常")
    void extractJson_shouldReturnNullWhenNoJson(String raw) {
        assertNull(parser.extractJson(raw));
    }

    @Test
    @DisplayName("正常返回：字段映射正确，success 由解析结果决定")
    void parseTravelResponse_shouldMapFields() {
        TravelRecommendVO vo = parser.parseTravelResponse("```json\n" + VALID_RESPONSE + "\n```");

        assertTrue(vo.getSuccess());
        assertEquals("北京", vo.getCity());
        assertEquals(2, vo.getDays());
        assertEquals(5000.0, vo.getTotalBudget());
        assertEquals(1, vo.getDailyItinerary().size());
        assertEquals("故宫", vo.getDailyItinerary().get(0).getMorning().getSpot());
        assertEquals(1500.0, vo.getBudgetBreakdown().getAccommodation());
        assertEquals(1, vo.getTips().size());
        assertNull(vo.getError());
    }

    @Test
    @DisplayName("模型没回填 success 字段时，解析成功也要置为 true——前端靠它决定是否渲染")
    void parseTravelResponse_shouldSetSuccessEvenIfModelOmitsIt() {
        String withoutSuccess = VALID_RESPONSE.replace("\"success\": true,", "");

        TravelRecommendVO vo = parser.parseTravelResponse(withoutSuccess);

        assertTrue(vo.getSuccess());
        assertEquals("北京", vo.getCity());
    }

    @Test
    @DisplayName("模型答非所问（纯文本）时给出失败结果并保留原文")
    void parseTravelResponse_shouldFailOnPlainText() {
        String raw = "抱歉，我暂时无法为你规划行程。";

        TravelRecommendVO vo = parser.parseTravelResponse(raw);

        assertFalse(vo.getSuccess());
        assertNotNull(vo.getError());
        assertEquals(raw, vo.getRawResponse());
    }

    @Test
    @DisplayName("JSON 合法但行程为空，同样判失败")
    void parseTravelResponse_shouldFailWhenItineraryMissing() {
        String raw = "{\"city\":\"北京\",\"days\":2,\"dailyItinerary\":[]}";

        TravelRecommendVO vo = parser.parseTravelResponse(raw);

        assertFalse(vo.getSuccess());
        assertTrue(vo.getError().contains("行程"));
    }

    @Test
    @DisplayName("返回内容被截断导致 JSON 不完整时判失败，不抛异常")
    void parseTravelResponse_shouldFailOnTruncatedJson() {
        String raw = "{\"city\":\"北京\",\"dailyItinerary\":[{\"day\":1";

        TravelRecommendVO vo = parser.parseTravelResponse(raw);

        assertFalse(vo.getSuccess());
        assertNotNull(vo.getError());
    }
}
