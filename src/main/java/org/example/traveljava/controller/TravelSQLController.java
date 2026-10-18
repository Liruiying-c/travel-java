package org.example.traveljava.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.traveljava.Mapper.ChatMapper;
import org.example.traveljava.service.ChatService;
import org.example.traveljava.service.UserService;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelChatVO;
import org.example.traveljava.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/travel")
@Slf4j
public class TravelSQLController {

    @Autowired
    ChatService chatService;

    @PostMapping("/chatget")
    public Result<List> chat(String userId) {
        // 根据userId查询聊天记录
//        System.out.println(userId);
        return chatService.getchat(userId);
    }

    @PostMapping("/chatsave")
    public Result<TravelChatVO> chatSave(@RequestBody TravelChatVO travelChatVO) {
        // 将返回的数据保存到数据库中
        return chatService.save(travelChatVO);
    }
}
