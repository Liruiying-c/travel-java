package org.example.traveljava.service;

import org.example.traveljava.Mapper.ChatMapper;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelChatVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @Mock
    private ChatMapper chatMapper;

    @InjectMocks
    private ChatService chatService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("保存聊天记录成功")
    void save_shouldInsert() {
        TravelChatVO chat = new TravelChatVO();
        chat.setUserId(1);
        chat.setUserMsg("北京有什么好吃的");
        chat.setAiMsg("烤鸭");
        chat.setUserTime(LocalDateTime.now());
        chat.setAiTime(LocalDateTime.now());

        Result result = chatService.save(chat);

        assertTrue(result.getSuccess());
        assertEquals("保存成功", result.getData());
        verify(chatMapper).insert(chat);
    }

    @Test
    @DisplayName("保存失败时返回错误信息，不把异常抛给前端")
    void save_shouldReturnErrorWhenMapperFails() {
        when(chatMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("db down"));

        Result result = chatService.save(new TravelChatVO());

        assertFalse(result.getSuccess());
        assertEquals("error", result.getError());
        assertEquals("db down", result.getRawResponse());
    }

    @Test
    @DisplayName("按用户查聊天记录")
    void getchat_shouldReturnList() {
        TravelChatVO chat = new TravelChatVO();
        chat.setUserMsg("你好");
        when(chatMapper.selectByUserId("1")).thenReturn(List.of(chat));

        Result<List> result = chatService.getchat("1");

        assertTrue(result.getSuccess());
        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("查询失败时也走统一错误结构")
    void getchat_shouldReturnErrorWhenMapperFails() {
        when(chatMapper.selectByUserId("1")).thenThrow(new RuntimeException("table missing"));

        Result<List> result = chatService.getchat("1");

        assertFalse(result.getSuccess());
        assertEquals("table missing", result.getRawResponse());
    }
}
