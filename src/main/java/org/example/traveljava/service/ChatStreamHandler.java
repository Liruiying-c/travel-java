package org.example.traveljava.service;

import org.example.traveljava.llm.LlmStreamListener;
import org.example.traveljava.vo.StreamChunkVO;
import org.example.traveljava.vo.StreamDoneVO;
import org.example.traveljava.vo.StreamErrorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 把模型的分片回调翻译成前端约定的 SSE 事件。
 *
 * <p>单独抽一个类，是为了让"事件顺序正确"这件事可以被单元测试直接验证：
 * 构造时传入一个收集器（测试里就是往 List 里塞），不需要起 Spring 容器、不需要真的 SSE 连接。</p>
 *
 * <p>它同时记录了完整回答 {@link #getFullText()}，这就是下一步接 Redis 会话记忆的挂点。</p>
 */
public class ChatStreamHandler implements LlmStreamListener {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamHandler.class);

    private final Consumer<Object> sink;
    private final StringBuilder fullText = new StringBuilder();

    private boolean finished;
    private boolean sinkBroken;

    public ChatStreamHandler(Consumer<Object> sink) {
        this.sink = sink;
    }

    @Override
    public boolean onDelta(String delta) {
        if (finished || sinkBroken || delta == null || delta.isEmpty()) {
            return !sinkBroken;
        }
        fullText.append(delta);
        send(StreamChunkVO.of(delta));
        return !sinkBroken;
    }

    @Override
    public void onComplete(String fullText) {
        if (finished) {
            return;
        }
        finished = true;
        send(StreamDoneVO.of());
    }

    @Override
    public void onError(Throwable error) {
        if (finished) {
            return;
        }
        finished = true;
        send(StreamErrorVO.of(toUserMessage(error)));
    }

    /** 本次对话的完整回答，供后续写入会话历史。 */
    public String getFullText() {
        return fullText.toString();
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isSinkBroken() {
        return sinkBroken;
    }

    private void send(Object payload) {
        if (sinkBroken) {
            return;
        }
        try {
            sink.accept(payload);
        } catch (Exception e) {
            // 前端断开连接时 send 会失败，标记一下，后续分片直接丢弃即可
            sinkBroken = true;
            log.warn("SSE 事件发送失败，后续分片将被丢弃：{}", e.toString());
        }
    }

    private String toUserMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "AI 服务异常，请稍后重试" : error.getMessage();
    }
}
