package org.example.traveljava.service;

import org.example.traveljava.vo.StreamChunkVO;
import org.example.traveljava.vo.StreamDoneVO;
import org.example.traveljava.vo.StreamErrorVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证"分片 → SSE 事件"的顺序与边界，这是前端能正常显示打字机效果的关键。
 */
class ChatStreamHandlerTest {

    private final List<Object> events = new ArrayList<>();

    @Test
    @DisplayName("正常结束：依次发出 chunk、chunk、done，并记录完整文本")
    void shouldEmitChunksThenDone() {
        ChatStreamHandler handler = new ChatStreamHandler(events::add);

        handler.onDelta("北京");
        handler.onDelta("欢迎你");
        handler.onComplete("北京欢迎你");

        assertEquals(3, events.size());
        assertEquals("北京", chunkContent(events.get(0)));
        assertEquals("欢迎你", chunkContent(events.get(1)));
        assertInstanceOf(StreamDoneVO.class, events.get(2));
        assertEquals("北京欢迎你", handler.getFullText());
        assertTrue(handler.isFinished());
    }

    @Test
    @DisplayName("出错：只发一条 error 事件，不发 done")
    void shouldEmitErrorOnly() {
        ChatStreamHandler handler = new ChatStreamHandler(events::add);

        handler.onDelta("部分内容");
        handler.onError(new IllegalStateException("连接超时"));

        assertEquals(2, events.size());
        assertInstanceOf(StreamErrorVO.class, events.get(1));
        assertEquals("连接超时", ((StreamErrorVO) events.get(1)).getError());
        assertFalse(events.stream().anyMatch(StreamDoneVO.class::isInstance));
    }

    @Test
    @DisplayName("前端断开后：发送失败不向外抛异常，后续分片被丢弃")
    void shouldSurviveBrokenSink() {
        List<Object> received = new ArrayList<>();
        ChatStreamHandler handler = new ChatStreamHandler(payload -> {
            if (received.size() >= 1) {
                throw new IllegalStateException("Broken pipe");
            }
            received.add(payload);
        });

        assertTrue(handler.onDelta("第一片"));
        assertFalse(handler.onDelta("第二片"));
        handler.onComplete("第一片第二片");

        assertEquals(1, received.size());
        assertTrue(handler.isSinkBroken());
    }

    @Test
    @DisplayName("结束之后到达的分片不再发送，避免前端收到 done 之后又来内容")
    void shouldIgnoreDeltaAfterFinish() {
        ChatStreamHandler handler = new ChatStreamHandler(events::add);

        handler.onComplete("完整内容");
        handler.onDelta("迟到的分片");
        handler.onError(new IllegalStateException("迟到的错误"));

        assertEquals(1, events.size());
        assertInstanceOf(StreamDoneVO.class, events.get(0));
    }

    @Test
    @DisplayName("空分片不产生事件")
    void shouldIgnoreEmptyDelta() {
        ChatStreamHandler handler = new ChatStreamHandler(events::add);

        handler.onDelta("");
        handler.onDelta(null);

        assertTrue(events.isEmpty());
    }

    private String chunkContent(Object event) {
        assertInstanceOf(StreamChunkVO.class, event);
        StreamChunkVO chunk = (StreamChunkVO) event;
        assertEquals("chunk", chunk.getType());
        return chunk.getContent();
    }
}
