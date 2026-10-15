package org.example.traveljava.llm;

import java.util.List;

/**
 * 大模型调用能力的抽象。
 *
 * <p>引入这层接口的三个理由：</p>
 * <ol>
 *   <li><b>可替换</b>：硅基流动、DeepSeek 都是 OpenAI 兼容协议，换服务商只改配置；
 *       如果以后接入协议完全不同的模型，只要再加一个实现类，业务代码一行不动。</li>
 *   <li><b>可测试</b>：Service 依赖接口，单元测试里可以注入假的实现，
 *       不用真的联网、不用等 180 秒、不会因为模型抽风导致测试不稳定。</li>
 *   <li><b>职责单一</b>：以前 LLMutils 同时管"组装请求、发 HTTP、解析响应、兜异常"，
 *       现在这些细节都被关在实现类里，上层只看到 chat / chatStream 两个动作。</li>
 * </ol>
 *
 * <p>实现类抛出的失败统一是 {@link LlmException}，流式方法不会向外抛异常。</p>
 */
public interface LlmClient {

    /**
     * 非流式对话，一次拿到完整回答。
     *
     * @throws LlmException 调用失败（超时、HTTP 错误、返回内容无法解析等）
     */
    LlmResult chat(List<ChatMessage> messages);

    /**
     * 流式对话，边生成边回调。
     *
     * @param listener 分片回调，结束时恰好触发 onComplete 或 onError 其中一个
     */
    void chatStream(List<ChatMessage> messages, LlmStreamListener listener);

    /**
     * 内部以流式方式获取回答，对外仍一次性返回完整结果。
     *
     * <p>用于"输出较长、耗时可能超过网络空闲读超时"的场景：非流式请求在模型思考与生成的
     * 整个过程中，连接上一个字节都没有，读超时一到就会被判定为失败（表现就是接口等了
     * 固定时长后返回超时错误）。流式则持续有分片到达，只要相邻分片的间隔不超过
     * stream-read-timeout，就不会被误杀。</p>
     *
     * <p>返回的 {@code rawResponse} 是各分片拼接后的文本（流式没有单一的原始响应体）。</p>
     *
     * @throws LlmException 调用失败或最终内容为空
     */
    LlmResult chatStreaming(List<ChatMessage> messages);

    /** 当前提供方（便于日志与排查）。 */
    String provider();

    /** 当前模型名。 */
    String model();
}
