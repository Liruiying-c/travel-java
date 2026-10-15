package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.llm.ChatMessage;
import org.example.traveljava.llm.LlmClient;
import org.example.traveljava.llm.LlmErrorKind;
import org.example.traveljava.llm.LlmException;
import org.example.traveljava.llm.LlmProperties;
import org.example.traveljava.llm.LlmResponseParser;
import org.example.traveljava.llm.LlmResult;
import org.example.traveljava.llm.LlmStreamListener;
import org.example.traveljava.vo.TravelRecommendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service 层测试：把 LlmClient 换成假的以后，不用联网、不用等 180 秒也能覆盖各种失败场景。
 * 这正是"抽接口"带来的直接收益。
 */
class TravelServiceTest {

    private static final String VALID_JSON = """
            {"city":"北京","days":2,"totalBudget":5000,
             "dailyItinerary":[{"day":1,"date":"第1天",
               "morning":{"spot":"故宫","duration":"3小时","ticket":"60元","transportation":"地铁","description":"必看"},
               "afternoon":{"spot":"景山","duration":"1小时","ticket":"2元","transportation":"步行","description":"俯瞰"},
               "evening":{"spot":"南锣鼓巷","duration":"2小时","ticket":"免费","transportation":"地铁","description":"小吃"}}],
             "budgetBreakdown":{"accommodation":1500,"food":1200,"transportation":800,"tickets":1000,"other":500}}
            """;

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties properties = new LlmProperties();

    private TravelService service(Executor executor) {
        properties.setSseTimeout(Duration.ofSeconds(5));
        return new TravelService(llmClient, new LlmResponseParser(objectMapper), properties, objectMapper, executor);
    }

    @Test
    @DisplayName("推荐成功：把模型回答解析成 VO，并把城市写进了提示词")
    void recommend_shouldReturnParsedItinerary() {
        when(llmClient.chat(anyList())).thenReturn(new LlmResult(VALID_JSON, VALID_JSON, "test-model", 42));

        TravelRecommendVO vo = service(Runnable::run).recommend("北京", 2, 5000.0);

        assertTrue(vo.getSuccess());
        assertEquals("北京", vo.getCity());
        assertEquals(1, vo.getDailyItinerary().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).chat(captor.capture());
        List<ChatMessage> messages = captor.getValue();
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).role());
        assertTrue(messages.get(0).content().contains("北京"));
        assertTrue(messages.get(0).content().contains("5000"));
    }

    @Test
    @DisplayName("模型超时：返回能看懂的提示，不把内部异常信息透给用户")
    void recommend_shouldReturnFriendlyMessageOnTimeout() {
        when(llmClient.chat(anyList()))
                .thenThrow(new LlmException(LlmErrorKind.TIMEOUT, "read-timeout=PT120S, 调用大模型超时"));

        TravelRecommendVO vo = service(Runnable::run).recommend("北京", 2, 5000.0);

        assertFalse(vo.getSuccess());
        assertEquals("模型响应超时，请稍后重试", vo.getError());
    }

    @Test
    @DisplayName("模型胡说八道（返回纯文本）：判失败而不是让前端拿到半截数据")
    void recommend_shouldFailWhenModelReturnsPlainText() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResult("抱歉，我无法完成", "抱歉，我无法完成", "test-model", 10));

        TravelRecommendVO vo = service(Runnable::run).recommend("北京", 2, 5000.0);

        assertFalse(vo.getSuccess());
        assertNotNull(vo.getError());
    }

    @Test
    @DisplayName("配置 recommend-via-stream=true 时改走内部流式，两条路径只差一个开关")
    void recommend_shouldUseStreamingWhenConfigured() {
        properties.setRecommendViaStream(true);
        when(llmClient.chatStreaming(anyList()))
                .thenReturn(new LlmResult(VALID_JSON, VALID_JSON, "test-model", 42));

        TravelRecommendVO vo = service(Runnable::run).recommend("北京", 2, 5000.0);

        assertTrue(vo.getSuccess());
        assertEquals("北京", vo.getCity());
        verify(llmClient).chatStreaming(anyList());
        verify(llmClient, never()).chat(anyList());
    }

    @Test
    @DisplayName("流式咨询：system + user 两条消息交给 LlmClient，分片由 ChatStreamHandler 承接")
    void chat_shouldAssembleMessagesAndUseHandler() {
        List<Runnable> tasks = new ArrayList<>();
        TravelService service = service(tasks::add);

        service.chat("北京有什么好吃的");
        tasks.forEach(Runnable::run);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LlmStreamListener> listenerCaptor = ArgumentCaptor.forClass(LlmStreamListener.class);
        verify(llmClient).chatStream(messagesCaptor.capture(), listenerCaptor.capture());

        List<ChatMessage> messages = messagesCaptor.getValue();
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertEquals("北京有什么好吃的", messages.get(1).content());

        // 这一步同时验证了下一步接 Redis 要用到的完整文本
        ChatStreamHandler handler = assertInstanceOf(ChatStreamHandler.class, listenerCaptor.getValue());
        handler.onDelta("烤鸭");
        assertEquals("烤鸭", handler.getFullText());
    }

    @Test
    @DisplayName("线程池打满：不抛异常给前端，而是返回一个带错误提示的 emitter")
    void chat_shouldNotThrowWhenExecutorRejects() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        SseEmitter emitter = assertDoesNotThrow(() -> service(rejectingExecutor).chat("北京有什么好吃的"));

        assertNotNull(emitter);
    }
}
