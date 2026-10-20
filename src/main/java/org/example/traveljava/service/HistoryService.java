package org.example.traveljava.service;


import org.example.traveljava.Mapper.HistoryMapper;
import org.example.traveljava.dto.TravelRequestDto;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.example.traveljava.vo.TravelSearchVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    @Autowired
    private HistoryMapper historyMapper;

    // 根据用户id+是否收藏返回页面数据
    public Result<List> historyList(Integer userId,Integer isCollect) {
        List<TravelSearchVO> res;
        try {
            if (isCollect == 1){
                res = historyMapper.selectCollect(userId);
            }else {
                res = historyMapper.selectALL(userId);
            }
            return Result.success(res);
        }catch (Exception e) {
            System.err.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }
    }

    // 返回具体行程
    public Result<TravelRecommendVO> selectRecommend(Integer id) {
        try {
//            System.out.println(id);
            // 首先查询t_search.id对应的tripId
            Integer tripId = historyMapper.selectTripId(id);
            TravelRecommendVO res = historyMapper.selectTripById(tripId);
//            System.out.println(res);
            return Result.success(res);
        }catch (Exception e) {
            System.err.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }
    }

    public Result saveTrip (TravelRecommendVO travelRecommendVO) {
        // 先插入详细行程，获取行程id
        try{
            historyMapper.insertTrip(travelRecommendVO);
//            System.out.println(travelRecommendVO.getTripId());
            historyMapper.insertSearch(travelRecommendVO);
            return Result.success(travelRecommendVO.getTripId()); // 返回行程下标
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }
    }

    // 收藏或取消收藏
    public Result updateCollect (Integer id,Integer isCollect) {
        try {
            historyMapper.updateCollect(id,isCollect);
            return Result.success("操作成功");
        }catch (Exception e) {
            System.err.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }
    }

    // 是否收藏
    public Result checkCollect (Integer id) {
        try {
            Integer isCollected = historyMapper.checkCollect(id);
            return Result.success(isCollected);
        }catch (Exception e) {
            System.err.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }
    }

    public Result deleteTrip (Integer id) {
        // 先查出对应的trip_id
        Integer tripId = historyMapper.selectTripId(id);
        System.out.println(tripId);
        // 再删除两个表中对应的数据
        try {
            historyMapper.deleteSearch(id);
            historyMapper.deleteTrip(tripId);
            return Result.success("删除成功");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Result.error("error", e.getMessage());
        }

    }
}
