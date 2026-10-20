package org.example.traveljava.controller;

import jakarta.servlet.http.Cookie;
import org.example.traveljava.service.ChatService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelChatVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TravelSQLController.class)
class TravelSQLControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    @DisplayName("chatget：按 userId 查聊天记录")
    void chatget_shouldDelegate() throws Exception {
        when(chatService.getchat("1")).thenReturn(Result.success(List.of()));

        mockMvc.perform(authed(post("/api/travel/chatget").param("userId", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(chatService).getchat("1");
    }

    @Test
    @DisplayName("chatsave：请求体完整映射到 VO 再交给 Service")
    void chatsave_shouldMapBody() throws Exception {
        when(chatService.save(any())).thenReturn(Result.success("保存成功"));

        mockMvc.perform(authed(post("/api/travel/chatsave"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"userMsg\":\"北京有什么好吃的\",\"aiMsg\":\"烤鸭\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("保存成功"));

        ArgumentCaptor<TravelChatVO> captor = ArgumentCaptor.forClass(TravelChatVO.class);
        verify(chatService).save(captor.capture());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals("北京有什么好吃的", captor.getValue().getUserMsg());
        assertEquals("烤鸭", captor.getValue().getAiMsg());
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("access_jwt", JwtUtils.generateJwt(Map.of("username", "tester"))));
    }
}
