package org.example.traveljava.service;


import jakarta.annotation.PostConstruct;
import org.example.traveljava.utils.LLMUtils;
import org.example.traveljava.vo.StreamChunkVO;
import org.example.traveljava.vo.StreamDoneVO;
import org.example.traveljava.vo.StreamErrorVO;
import org.example.traveljava.vo.TravelRecommendVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

@Service
public class TravelService {
    @Value("${llm.api-key}")
    private String apikey;
    @Value("${llm.base-url}")
    private String baseUrl;
    @Value("${llm.model}")
    private String model;

    private LLMUtils llmutils;
    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct // 通过注解导入无法用构造函数直接初始化，需使用注解初始化
    public void init() {
        llmutils = new LLMUtils(apikey, baseUrl, model);
    }

    // 处理旅行推荐逻辑
    public TravelRecommendVO recommend(String city,Integer days,Double budget) {
        TravelRecommendVO result = new TravelRecommendVO();
        String prompt = buildTravelPrompt(city,budget,days);
        try{
            String response = llmutils.chat(null,prompt);
            return parseTravelResponse(response);
        }catch (Exception e) {
            result.setSuccess(false);
            result.setError("旅游规划失败" + e.getMessage());
            return result;
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


    // service旅游推荐返回数据处理
    private TravelRecommendVO parseTravelResponse(String response) {
        TravelRecommendVO result = new TravelRecommendVO();

        try {
            String jsonContent = extractJson(response);
            if (jsonContent != null) {
                result = objectMapper.readValue(jsonContent, TravelRecommendVO.class);
            } else {
                result.setSuccess(false);
                result.setError("未能从响应中提取JSON");
                result.setRawResponse(response);
            }
        } catch (Exception e) {
            // log.error("解析旅游响应失败", e);
            result.setSuccess(false);
            result.setError("JSON解析失败");
            result.setRawResponse(response);
        }

        return result;
    }

    private String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        String[] patterns = {
                "```json\\n([\\s\\S]*?)\\n```",
                "```\\n([\\s\\S]*?)\\n```"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(response);
            if (m.find()) {
                return m.group(1);
            }
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }


    public SseEmitter chat(String message) {
        SseEmitter emitter = new SseEmitter(1800000L);

        // 发送处理逻辑
        // 通过线程处理大模型返回的流式数据
        new Thread(() -> {
            try {
                String systemPrompt = "你是一个专业的旅游规划师，负责为用户推荐旅游计划。请用中文回答用户的问题。";

                Consumer<String> callback = content -> {
                    try {
                        String Json = objectMapper.writeValueAsString(StreamChunkVO.of(content));
                        emitter.send(SseEmitter.event().data(Json));
                    }catch (Exception e) {
                        System.out.println("发送消息失败：" + e);
                    }
                };
//
                llmutils.chatStream(systemPrompt,message,callback);
                // 发送完成
                String DoneJson = objectMapper.writeValueAsString(StreamDoneVO.of());
                emitter.send(SseEmitter.event().data(DoneJson));
                emitter.complete();
            } catch(Exception e) {
                try{
                    String errorJson = objectMapper.writeValueAsString(StreamErrorVO.of(e.getMessage()));
                    emitter.send(SseEmitter.event().data(errorJson));
                }catch(Exception ex){
                    System.out.println("发送错误消息失败：" + ex);
                }
                emitter.completeWithError(e);
            }

        }).start();
        return emitter;
    }
}
