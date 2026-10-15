package org.example.traveljava.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 相关 Bean 的装配。
 *
 * <p>以后要接第二家协议不兼容的模型服务商，做法是：</p>
 * <pre>
 * new 一个 XxxLlmClient implements LlmClient
 * 然后加一个 {@code @Bean @ConditionalOnProperty(name = "llm.provider", havingValue = "xxx")} 的装配方法
 * </pre>
 * 因为下面已经用 {@code @ConditionalOnMissingBean} 兜底，业务代码完全不用改。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

    @Bean
    @ConditionalOnMissingBean(OkHttpClient.class)
    public OkHttpClient llmHttpClient(LlmProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .writeTimeout(properties.getReadTimeout())
                .retryOnConnectionFailure(true)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient llmClient(OkHttpClient llmHttpClient,
                               LlmProperties properties,
                               ObjectMapper objectMapper) {
        return new OkHttpLlmClient(llmHttpClient, properties, objectMapper);
    }

    /**
     * 兜底的 ObjectMapper。
     *
     * <p>Spring Boot 4 同时存在 Jackson 2 与 Jackson 3 两套自动配置，
     * 这里显式兜一个，保证注入到的一定是可用的那个实例。</p>
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper llmObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * 流式请求的工作线程池。
     *
     * <p>原来是每个请求 {@code new Thread(...)}，请求量一上来线程数就不可控；
     * 换成有界队列 + 固定线程数的线程池后，过载时会明确拒绝，而不是把机器拖垮。</p>
     */
    @Bean(name = "llmExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor llmExecutor(LlmProperties properties) {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "llm-worker-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                properties.getWorkerThreads(),
                properties.getWorkerThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

}
