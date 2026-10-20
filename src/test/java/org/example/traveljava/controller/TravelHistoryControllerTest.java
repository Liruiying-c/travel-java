package org.example.traveljava.controller;

import jakarta.servlet.http.Cookie;
import org.example.traveljava.Mapper.HistoryMapper;
import org.example.traveljava.service.HistoryService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 这些接口都是"参数 → Service → Result"的薄壳。
 * 测试价值在于参数名和 Service 方法签名对得上：写错一个参数名，前端就静默拿到 null。
 */
@WebMvcTest(TravelHistoryController.class)
class TravelHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HistoryService historyService;

    @MockitoBean
    private HistoryMapper historyMapper;

    @Test
    @DisplayName("history：userId / collected 两个参数都传到位")
    void history_shouldPassBothParams() throws Exception {
        when(historyService.historyList(1, 1)).thenReturn(Result.success(List.of()));

        mockMvc.perform(authed(post("/api/travel/history").param("userId", "1").param("collected", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(historyService).historyList(1, 1);
    }

    @Test
    @DisplayName("tripDetail：按 id 查详情")
    void tripDetail_shouldDelegate() throws Exception {
        TravelRecommendVO vo = new TravelRecommendVO();
        vo.setCity("北京");
        when(historyService.selectRecommend(5)).thenReturn(Result.success(vo));

        mockMvc.perform(authed(post("/api/travel/tripDetail").param("id", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("北京"));

        verify(historyService).selectRecommend(5);
    }

    @Test
    @DisplayName("collected：切换收藏状态")
    void collected_shouldDelegate() throws Exception {
        when(historyService.updateCollect(9, 1)).thenReturn(Result.success("操作成功"));

        mockMvc.perform(authed(post("/api/travel/collected").param("id", "9").param("collected", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("操作成功"));

        verify(historyService).updateCollect(9, 1);
    }

    @Test
    @DisplayName("checkCollect：查询是否收藏")
    void checkCollect_shouldDelegate() throws Exception {
        when(historyService.checkCollect(9)).thenReturn(Result.success(1));

        mockMvc.perform(authed(post("/api/travel/checkCollect").param("id", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(historyService).checkCollect(9);
    }

    @Test
    @DisplayName("delete_trip：删除")
    void deleteTrip_shouldDelegate() throws Exception {
        when(historyService.deleteTrip(9)).thenReturn(Result.success("删除成功"));

        mockMvc.perform(authed(post("/api/travel/delete_trip").param("id", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("删除成功"));

        verify(historyService).deleteTrip(9);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("access_jwt", JwtUtils.generateJwt(Map.of("username", "tester"))));
    }
}
