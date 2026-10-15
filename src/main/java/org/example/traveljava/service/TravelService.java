package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.llm.ChatMessage;
import org.example.traveljava.llm.LlmClient;
import org.example.traveljava.llm.LlmException;
import org.example.traveljava.llm.LlmProperties;
import org.example.traveljava.llm.LlmResponseParser;
import org.example.traveljava.llm.LlmResult;
import org.example.traveljava.vo.StreamErrorVO;
import org.example.traveljava.vo.TravelRecommendVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 旅游业务逻辑。
 *
 * <p>重构后它只做三件事：拼业务提示词、调用 {@link LlmClient}、把结果交给
 * {@link LlmResponseParser}。HTTP 细节、JSON 转义、超时管理、异常分类全部下沉到 llm 包，
 * 业务代码里不再出现 okhttp 与手工拼 JSON 的痕迹。</p>
 */
@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);
    private static final String CHAT_SYSTEM_PROMPT =
            "你是一个专业的旅游规划师，负责为用户推荐旅游计划。请用中文回答用户的问题。";

    private final LlmClient llmClient;
    private final LlmResponseParser responseParser;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor llmExecutor;

    public TravelService(LlmClient llmClient,
                         LlmResponseParser responseParser,
                         LlmProperties properties,
                         ObjectMapper objectMapper,
                         @Qualifier("llmExecutor") Executor llmExecutor) {
        this.llmClient = llmClient;
        this.responseParser = responseParser;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.llmExecutor = llmExecutor;
    }

    /** 生成行程规划（非流式）。 */
    public TravelRecommendVO recommend(String city, Integer days, Double budget) {
        String prompt = buildTravelPrompt(city, budget, days);
        List<ChatMessage> messages = List.of(ChatMessage.user(prompt));
        try {
            // 两条路径发出去的请求只差一个 stream 字段，用配置切换，方便对着耗时做对比：
            // 阻塞式：适合"服务商非流式路径更快"的情况，读超时要给足（见 llm.read-timeout）
            // 内部流式：适合"生成很久、怕被网络空闲读超时误杀"的情况
            LlmResult result = properties.isRecommendViaStream()
                    ? llmClient.chatStreaming(messages)
                    : llmClient.chat(messages);
            TravelRecommendVO vo = responseParser.parseTravelResponse(result.content());
            log.info("行程生成完成 city={} days={} 模式={} 耗时={}ms 输出字符数={} success={}",
                    city, days, properties.isRecommendViaStream() ? "stream" : "blocking",
                    result.latencyMs(), result.content().length(), vo.getSuccess());
            return vo;
        } catch (LlmException e) {
            log.warn("行程生成失败 city={} kind={} message={}", city, e.getKind(), e.getMessage());
            TravelRecommendVO vo = new TravelRecommendVO();
            vo.setSuccess(false);
            vo.setError(friendlyMessage(e));
            return vo;
        }
    }

    /** AI 旅游咨询（流式）。 */
    public SseEmitter chat(String message) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        emitter.onTimeout(() -> log.warn("SSE 连接超时，客户端可能已离线"));
        emitter.onError(e -> log.warn("SSE 连接异常：{}", e.toString()));

        List<ChatMessage> messages = List.of(
                ChatMessage.system(CHAT_SYSTEM_PROMPT),
                ChatMessage.user(message));
        try {
            llmExecutor.execute(() -> streamChat(messages, emitter));
        } catch (RejectedExecutionException e) {
            log.warn("流式线程池已满，拒绝本次请求");
            sendQuietly(emitter, StreamErrorVO.of("当前咨询人数较多，请稍后重试"));
            emitter.complete();
        }
        return emitter;
    }

    void streamChat(List<ChatMessage> messages, SseEmitter emitter) {
        try {
            ChatStreamHandler handler = new ChatStreamHandler(payload -> sendOrThrow(emitter, payload));
            llmClient.chatStream(messages, handler);
            // TODO(下一步：Redis 会话记忆) 把 handler.getFullText() 作为 assistant 回复写入会话历史
            emitter.complete();
        } catch (Exception e) {
            log.warn("流式会话异常结束：{}", e.toString());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // 连接可能已经断开，忽略
            }
        }
    }

    /** 把技术异常翻译成用户能看懂的话，不把内部细节直接透给前端。 */
    private String friendlyMessage(LlmException e) {
        return switch (e.getKind()) {
            case CONFIG_ERROR -> "服务尚未正确配置，请联系管理员";
            case TIMEOUT -> "模型响应超时，请稍后重试";
            case NETWORK_ERROR -> "网络异常，暂时无法连接模型服务";
            case HTTP_ERROR -> e.getStatusCode() != null && e.getStatusCode() == 429
                    ? "请求过于频繁，请稍后重试"
                    : "模型服务暂时不可用，请稍后重试";
            case EMPTY_RESPONSE, PARSE_ERROR -> "模型返回内容异常，请重新发起一次规划";
            default -> "旅游规划失败，请稍后重试";
        };
    }

    private void sendOrThrow(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 事件发送失败", e);
        }
    }

    private void sendQuietly(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.debug("发送 SSE 事件失败：{}", e.toString());
        }
    }

    private String buildTravelPrompt(String city, Double budget, Integer days) {
        return "你是一个专业的旅游规划师，擅长根据用户的需求生成详细的旅行行程。\n\n" +
                "请根据以下信息为用户生成一份详细的旅游规划：\n" +
                "- 目的地城市：" + city + "\n" +
                "- 预算：" + budget + "元\n" +
                "- 旅行天数：" + days + "天\n\n" +
                "要求：\n" +
                "1. 每天的行程安排（上午、下午、晚上）\n" +
                "2. 每个景点的详细介绍\n" +
                "3. 交通建议\n" +
                "4. 预算分配明细\n" +
                "5. 注意事项\n\n" +
                "请以JSON格式输出，结构如下：\n" +
                "{\n" +
                "  \"success\": true,\n" +
                "  \"city\": \"城市名\",\n" +
                "  \"days\": 天数,\n" +
                "  \"totalBudget\": 总预算,\n" +
                "  \"dailyItinerary\": [\n" +
                "    {\n" +
                "      \"day\": 1,\n" +
                "      \"date\": \"第1天\",\n" +
                "      \"morning\": {\n" +
                "        \"spot\": \"景点名称\",\n" +
                "        \"duration\": \"游览时长\",\n" +
                "        \"ticket\": \"门票价格\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"景点介绍\"\n" +
                "      },\n" +
                "      \"afternoon\": {\n" +
                "        \"spot\": \"景点名称\",\n" +
                "        \"duration\": \"游览时长\",\n" +
                "        \"ticket\": \"门票价格\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"景点介绍\"\n" +
                "      },\n" +
                "      \"evening\": {\n" +
                "        \"spot\": \"活动名称\",\n" +
                "        \"duration\": \"活动时长\",\n" +
                "        \"ticket\": \"费用\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"活动介绍\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"budgetBreakdown\": {\n" +
                "    \"accommodation\": 住宿费用,\n" +
                "    \"food\": 餐饮费用,\n" +
                "    \"transportation\": 交通费用,\n" +
                "    \"tickets\": 门票费用,\n" +
                "    \"other\": 其他费用\n" +
                "  },\n" +
                "  \"tips\": [\"提示1\", \"提示2\", \"提示3\"],\n" +
                "  \"warnings\": [\"注意事项1\", \"注意事项2\"]\n" +
                "}\n\n" +
                "请确保JSON格式正确，可以被解析。";
    }
}
