package org.example.traveljava.llm;

/**
 * 一条对话消息，对应 OpenAI 兼容协议里 messages 数组的一个元素。
 *
 * <p>把它抽出来以后，会话历史（下一步要接 Redis）和单轮请求用的是同一个模型，
 * 上层不需要再关心"拼字符串"这件事。</p>
 */
public record ChatMessage(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }
}
