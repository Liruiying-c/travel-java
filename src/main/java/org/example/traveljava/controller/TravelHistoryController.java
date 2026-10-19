package org.example.traveljava.controller;


import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.example.traveljava.Mapper.HistoryMapper;
import org.example.traveljava.service.HistoryService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;

import java.util.List;

@RestController
@RequestMapping("/api/travel")
public class TravelHistoryController {

    @Autowired
    private HistoryService historyService;
    @Autowired
    private HistoryMapper historyMapper;

    @PostMapping("/history")
    public Result<List> historyList(Integer userId,Integer collected){
//        System.out.println(userId + collected);
        return historyService.historyList(userId,collected);
    }

    @PostMapping("/tripDetail")
    public Result<TravelRecommendVO> travelDetail(Integer id){
        return historyService.selectRecommend(id);
    }

    @PostMapping("/collected")
    public Result swapCollected(Integer id,Integer collected){
        return historyService.updateCollect(id,collected);
    }

    @PostMapping("/checkCollect")
    public Result isCollected(Integer id){
        return historyService.checkCollect(id);
    }

    @PostMapping("/delete_trip")
    public Result deleteTrip(Integer id){
        return historyService.deleteTrip(id);
    }

}