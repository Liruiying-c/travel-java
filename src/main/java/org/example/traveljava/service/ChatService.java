package org.example.traveljava.service;

import org.example.traveljava.Mapper.ChatMapper;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelChatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatMapper chatMapper;

    public Result save(TravelChatVO travelChatVO) {
        try {
            chatMapper.insert(travelChatVO);
            return Result.success("保存成功");
        }catch(Exception e) {
            return Result.error("error",e.getMessage());
        }
    }

    public Result<List> getchat(String userId) {
        try {
            List<TravelChatVO> res = chatMapper.selectByUserId(userId);
//            System.out.println(res);
            return Result.success(res);
        }catch(Exception e) {
            return Result.error("error",e.getMessage());
        }
    }
}
