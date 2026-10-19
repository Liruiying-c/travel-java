package org.example.traveljava.Mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.traveljava.vo.UserVO;

@Mapper
public interface UserMapper {
    @Select("select * from t_user where username = #{username}")
    public UserVO SelectByUsername(String username);

    @Insert("insert into t_user(username,password_hash,nickname,status) values (#{username},#{passwordHash},#{nickname},1)")
    int insert(UserVO userVO);

    @Update("update t_user set nickname = #{nickName} where id = #{userId}")
    public int updateNickname(Integer userId,String nickName);

    @Select("select password_hash from t_user where id = #{userId}")
    public String selectPassword(Integer userId);

    @Update("update t_user set password_hash = #{newPassword} where id = #{userId}")
    public int updatePassword(Integer userId,String newPassword);

}
