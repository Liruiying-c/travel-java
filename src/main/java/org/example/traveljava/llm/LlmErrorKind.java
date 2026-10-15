package org.example.traveljava.llm;

/**
 * 大模型调用的失败分类。
 *
 * <p>分类的意义在于：上层可以据此决定"要不要重试"、"给用户看什么提示"，
 * 而不是把所有失败都变成一句无差别的 500。</p>
 */
public enum LlmErrorKind {

    /** 未配置 api-key / model / base-url */
    CONFIG_ERROR,

    /** 连接或读取超时，可重试 */
    TIMEOUT,

    /** 网络不可达、DNS 解析失败等，可重试 */
    NETWORK_ERROR,

    /** 接口返回非 2xx，4xx 通常重试无意义，5xx 可以重试 */
    HTTP_ERROR,

    /** 返回内容不是合法 JSON */
    PARSE_ERROR,

    /** 返回体为空、choices 为空、content 为空 */
    EMPTY_RESPONSE,

    UNKNOWN
}
