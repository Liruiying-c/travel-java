package org.example.traveljava.Mapper;


import org.apache.ibatis.annotations.*;
import org.example.traveljava.vo.TravelRecommendVO;
import org.example.traveljava.vo.TravelSearchVO;

import java.util.List;

@Mapper
public interface HistoryMapper {

    @Select("select s.*, t.city,t.days,t.total_budget from t_search s, t_trip t where s.user_id = #{userId} and s.is_collected = 1 and s.trip_id = t.id order by s.create_time desc")
    public List<TravelSearchVO> selectCollect (Integer userId);

    @Select("select s.*, t.city,t.days,t.total_budget from t_search s, t_trip t where s.user_id = #{userId} and s.trip_id = t.id order by s.create_time desc")
    public List<TravelSearchVO> selectALL (Integer userId);

    public TravelRecommendVO selectTripById(Integer id);

    @Insert("insert into t_search(user_id,trip_id) values (#{userId},#{tripId})")
    @Options(useGeneratedKeys = true, keyProperty = "tripId")
    public void insertSearch(TravelRecommendVO recommendVO);

    public Integer insertTrip(TravelRecommendVO recommendVO);

    @Update("update t_search set is_collected = #{isCollected} where id = #{id}")
    public void updateCollect(Integer id,Integer isCollected);

    @Select("select trip_id from t_search where id = #{id}")
    public Integer selectTripId(Integer id);

    @Select("select t_search.is_collected from t_search where id = #{id}")
    public Integer checkCollect(Integer id);

    @Delete("delete from t_trip where id = #{tripId}")
    public void deleteTrip(Integer tripId);

    @Delete("delete from t_search where id = #{id}")
    public void deleteSearch(Integer id);

}
