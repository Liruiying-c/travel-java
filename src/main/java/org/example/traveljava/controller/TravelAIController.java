package org.example.traveljava.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.traveljava.dto.ChatRequestDto;
import org.example.traveljava.dto.TravelRequestDto;
import org.example.traveljava.service.TravelService;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/travel")
@AllArgsConstructor
public class TravelAIController {

    private final TravelService travelService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello World!");
    }

    @PostMapping("/recommend")
    public Result<TravelRecommendVO> recommend(@Valid @RequestBody TravelRequestDto travelRequestDto) { // 开启参数校验 校验请求的参数
        TravelRecommendVO result = travelService.recommend(travelRequestDto.getCity(), travelRequestDto.getDays(), travelRequestDto.getBudget());
        return Result.success(result);
    }

    @PostMapping(path = "/chat", produces = "text/event-stream")
    public SseEmitter chat(@Valid @RequestBody ChatRequestDto chatRequestDto) {
        return travelService.chat(chatRequestDto.getMessage());
    }


}
