package org.example.traveljava.controller;

import jakarta.servlet.http.Cookie;
import org.example.traveljava.service.HistoryService;
import org.example.traveljava.service.TravelService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 层只负责"接参数、调 Service、包结果"。
 * 这里重点验证参数校验边界——这正是 AI 生成代码最爱漏的地方。
 */
@WebMvcTest(TravelAIController.class)
class TravelAIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelService travelService;

    @MockitoBean
    private HistoryService historyService;

    @Test
    @DisplayName("hello 能通，说明上下文和路由没问题")
    void hello_shouldReturnText() throws Exception {
        mockMvc.perform(authed(get("/api/travel/hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Hello World!"));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("{\"budget\":5000,\"days\":3}", "城市不能为空"),
                Arguments.of("{\"city\":\"\",\"budget\":5000,\"days\":3}", "城市不能为空"),
                Arguments.of("{\"city\":\"北京\",\"budget\":99,\"days\":3}", "预算不能小于100"),
                Arguments.of("{\"city\":\"北京\",\"budget\":5000,\"days\":0}", "天数不能小于1"),
                Arguments.of("{\"city\":\"北京\",\"budget\":5000,\"days\":31}", "天数不能大于30"),
                Arguments.of("{\"city\":\"北京\",\"days\":3}", "预算不能为空")
        );
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("invalidRequests")
    @DisplayName("非法参数：在进 Service 之前就被拦下")
    void recommend_shouldRejectInvalidInput(String body, String expectedMessage) throws Exception {
        mockMvc.perform(authed(post("/api/travel/recommend"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        verifyNoInteractions(travelService);
        verifyNoInteractions(historyService);
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of("{\"city\":\"北京\",\"budget\":100,\"days\":1}"),
                Arguments.of("{\"city\":\"北京\",\"budget\":5000,\"days\":30}")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validRequests")
    @DisplayName("合法边界值（budget=100、days=1/30）能通过校验")
    void recommend_shouldAcceptBoundaryValues(String body) throws Exception {
        when(travelService.recommend(anyString(), any(), any())).thenReturn(planned());
        when(historyService.saveTrip(any())).thenReturn(Result.success());

        mockMvc.perform(authed(post("/api/travel/recommend"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.city").value("北京"));
    }

    @Test
    @DisplayName("生成成功后把 userId 带上，并落历史")
    void recommend_shouldSaveHistoryWithUserId() throws Exception {
        when(travelService.recommend(anyString(), any(), any())).thenReturn(planned());
        when(historyService.saveTrip(any())).thenReturn(Result.success());

        mockMvc.perform(authed(post("/api/travel/recommend"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"北京\",\"budget\":5000,\"days\":3,\"userId\":7}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TravelRecommendVO> captor = ArgumentCaptor.forClass(TravelRecommendVO.class);
        verify(historyService).saveTrip(captor.capture());
        assertEquals(7, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("chat 返回 SSE 流，而不是普通 JSON")
    void chat_shouldStartSse() throws Exception {
        when(travelService.chat("你好")).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60_000L));

        mockMvc.perform(authed(post("/api/travel/chat"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @DisplayName("chat 消息为空：400，不进入 Service")
    void chat_shouldRejectEmptyMessage() throws Exception {
        mockMvc.perform(authed(post("/api/travel/chat"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("消息不能为空"));

        verify(travelService, never()).chat(anyString());
    }

    /** 带上合法 JWT，避开登录拦截器——拦截器本身有独立测试。 */
    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("access_jwt", JwtUtils.generateJwt(Map.of("username", "tester"))));
    }

    private TravelRecommendVO planned() {
        TravelRecommendVO vo = new TravelRecommendVO();
        vo.setSuccess(true);
        vo.setCity("北京");
        vo.setDays(3);
        vo.setTotalBudget(5000.0);
        return vo;
    }
}
