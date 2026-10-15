package org.example.traveljava.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型相关配置。
 *
 * <p>原来这些值（apiKey / baseUrl / model）散落在 Service 的 @Value 里，
 * 超时时间则硬编码在 LLMutils 构造函数里（120 秒），测试根本没法把超时调短。
 * 集中成配置类之后，超时是"可注入的参数"，这正好是为可测试性做的一次重构。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 接口地址前缀，OpenAI 兼容协议，实际请求路径为 {base-url}/chat/completions */
    private String baseUrl = "https://api.siliconflow.cn/v1";

    /** 密钥，通过环境变量 LLM_API_KEY 注入，不要写进代码仓库 */
    private String apiKey;

    /** 模型名 */
    private String model;

    private Double temperature = 0.7;

    /** 可选：不填则由服务端默认 */
    private Integer maxTokens;

    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * 非流式请求的读超时。
     *
     * <p>这是"连接上多久没有数据就算失败"。模型生成一份完整行程可能要一两分钟以上，
     * 期间没有任何字节下发，所以这个值必须给足；真正长耗时的场景建议改用
     * {@link LlmClient#chatStreaming}，靠持续到达的分片刷新计时，而不是靠调大这个值硬等。</p>
     */
    private Duration readTimeout = Duration.ofSeconds(300);

    /** 流式请求的读超时：分片之间有间隔，允许比非流式更宽松 */
    private Duration streamReadTimeout = Duration.ofSeconds(180);

    /** SSE 连接自身的存活时间（SseEmitter 超时） */
    private Duration sseTimeout = Duration.ofMinutes(30);

    /** 处理流式请求的线程数 */
    private int workerThreads = 4;

    /** 排队等待的流式请求上限，满了直接拒绝，避免无限堆积 */
    private int queueCapacity = 200;

    /**
     * 行程推荐走哪条路径：false = 阻塞式 chat()（默认），true = 内部流式 chatStreaming()。
     *
     * <p>两者发出去的请求体只差一个 stream 字段。之所以做成开关，是因为实测发现
     * 某些服务商在流式模式下会走"思考"路径、耗时明显变长；改这一项重启后重发
     * 同一个请求，对比日志里的「耗时=」与「输出字符数=」就能定论。</p>
     */
    private boolean recommendViaStream = false;

    /**
     * 追加进请求体的服务商专有参数，例如 {@code enable_thinking: false}。
     *
     * <p>字段名以服务商文档为准，这里只是给"模型默认开了思考、想关掉"这类场景一个
     * 不用改代码的入口。注意：若 key 与标准字段同名，会覆盖标准字段。</p>
     */
    private Map<String, Object> extraParams = new LinkedHashMap<>();

    public boolean isApiKeyPresent() {
        return apiKey != null && !apiKey.isBlank();
    }
}
