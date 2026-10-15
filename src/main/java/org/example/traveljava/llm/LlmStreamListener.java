package org.example.traveljava.llm;

/**
 * 流式回调。
 *
 * <p>约定：一次调用结束时，{@link #onComplete} 与 {@link #onError} 必定恰好触发其中一个，
 * 并且不会向上抛出异常——调用方不需要自己写 try/catch。</p>
 */
public interface LlmStreamListener {

    /**
     * 收到一个分片。
     *
     * @return true 表示继续接收；false 表示下游已断开（例如前端断开了 SSE 连接），
     *         客户端会立即停止读取，避免继续消耗 token
     */
    boolean onDelta(String delta);

    /** 正常结束，fullText 是本次拼接出的完整回答。 */
    default void onComplete(String fullText) {
    }

    /** 异常结束。 */
    default void onError(Throwable error) {
    }
}
