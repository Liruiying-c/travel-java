package org.example.traveljava.Mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.traveljava.vo.TravelChatVO;

import java.util.List;

@Mapper
public interface ChatMapper {

    @Insert("insert into t_chat(user_id, user_msg, user_time, ai_msg, ai_time) values (#{userId},#{userMsg},#{userTime},#{aiMsg},#{aiTime})")
    public int insert(TravelChatVO travelChatVO);

    @Select("select * from t_chat where user_id = #{userId}")
    public List<TravelChatVO> selectByUserId(String userId);
}
