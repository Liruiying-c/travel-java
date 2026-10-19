package org.example.traveljava.service;

import org.example.traveljava.Mapper.UserMapper;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 登录，验证是否存在这个用户，且密码是否正确
    public UserVO login(String username, String rawPassword,String nickname) {
       UserVO e = userMapper.SelectByUsername(username);
//       System.out.println(nickname);
//       System.out.println(e.getNickname());
       if(e == null || !nickname.equals(e.getNickname())) {
           return null;
       }
       // 后端做密码比对
        boolean match = passwordEncoder.matches(rawPassword,e.getPasswordHash());
       System.out.println(match);
       if(!match) {
           return null;
       }
       // 校验通过
        return e;
    }

    // 注册用户
    public UserVO register(UserVO userVO) {
        // 判断当前用户是否已经存在
        UserVO user = userMapper.SelectByUsername(userVO.getUsername());
        if(user != null){
            return null;
        }
        // 加密，生成hash串存入表
        String hashPwd = passwordEncoder.encode(userVO.getPasswordHash());
        // 保存到数据库
        UserVO e = userVO;
        e.setPasswordHash(hashPwd);
        userMapper.insert(e);
        e = userMapper.SelectByUsername(userVO.getUsername());
        return e;
    }

    // 修改昵称
    public Result updateNickname(Integer userId,String nickName) {
        try {
            userMapper.updateNickname(userId,nickName);
            return Result.success("更新成功");
        }catch (Exception e){
            System.out.println(e.getMessage());
            return Result.error("error",e.getMessage());
        }
    }

    // 修改密码
    public Result updatePassword(Integer userId,String oldPassword, String newPassword) {
        // 通过用户id查到当前密码
        String passwordHash = userMapper.selectPassword(userId);
        // 对比
        boolean match = passwordEncoder.matches(oldPassword,passwordHash);
        if(!match) {
            return Result.fail(401,"密码错误");
        }else {
            // 存入新密码
            newPassword = passwordEncoder.encode(newPassword);
            userMapper.updatePassword(userId,newPassword);
            return Result.success("修改密码成功");
        }
    }
}
