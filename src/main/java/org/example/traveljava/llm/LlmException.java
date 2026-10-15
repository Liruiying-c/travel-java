package org.example.traveljava.llm;

/**
 * LLM 调用相关的统一异常，携带失败分类与 HTTP 状态码。
 */
public class LlmException extends RuntimeException {

    private final LlmErrorKind kind;
    private final Integer statusCode;

    public LlmException(LlmErrorKind kind, String message) {
        this(kind, message, null, null);
    }

    public LlmException(LlmErrorKind kind, String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public LlmErrorKind getKind() {
        return kind;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    /** 是否值得重试。 */
    public boolean isRetryable() {
        if (kind == LlmErrorKind.TIMEOUT || kind == LlmErrorKind.NETWORK_ERROR) {
            return true;
        }
        return kind == LlmErrorKind.HTTP_ERROR && statusCode != null && statusCode >= 500;
    }
}
