package org.example.traveljava.controller;


import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.traveljava.service.TravelService;
import org.example.traveljava.service.UserService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/travel")
@AllArgsConstructor
public class TravelLoginController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody UserVO user, HttpServletResponse response) {
        UserVO e = userService.login(user.getUsername(), user.getPasswordHash(),user.getNickname()); // 验证是否存在这个用户，且密码是否正确
        if(e != null) {
            Map<String,Object> claims = new HashMap<>();
            claims.put("username",e.getUsername());
            claims.put("password",e.getPasswordHash());

            String jwt = JwtUtils.generateJwt(claims);

            // 设置Cookie
            ResponseCookie cookie = ResponseCookie.from("access_jwt",jwt)
                    .httpOnly(true)
                    .secure(false)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(12 * 3600)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());

            user.setId(e.getId());
            user.setCreateTime(e.getCreateTime());
            user.setPasswordHash(null);
            return Result.success(user);
        }
        return Result.fail(401,"用户名或密码错误");
    }

    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody UserVO user,HttpServletResponse response) {
        UserVO e = userService.register(user);
        if(e == null) return Result.fail(401,"当前电话号码已注册");
        Map<String,Object> claims = new HashMap<>();
        claims.put("username",e.getUsername());
        claims.put("password",e.getPasswordHash());

        String jwt = JwtUtils.generateJwt(claims);

        // 设置Cookie
        ResponseCookie cookie = ResponseCookie.from("access_jwt",jwt)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(12 * 3600)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());

        e.setId(e.getId());
        e.setCreateTime(e.getCreateTime());
        e.setPasswordHash(null);
        return Result.success(e);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletResponse response){
        ResponseCookie cookie = ResponseCookie.from("access_jwt", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0) // maxAge=0 立刻销毁cookie
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.success("退出成功");
    }


}
