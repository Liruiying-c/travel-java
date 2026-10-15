package org.example.traveljava.llm;

/**
 * 一次非流式调用的结果。
 *
 * @param content     模型回答正文（choices[0].message.content）
 * @param rawResponse 原始响应体，排查"模型不听话"时全靠它
 * @param model       实际使用的模型名
 * @param latencyMs   本次调用耗时（毫秒）
 */
public record LlmResult(String content, String rawResponse, String model, long latencyMs) {
}
