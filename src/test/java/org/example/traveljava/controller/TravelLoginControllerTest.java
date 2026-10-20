package org.example.traveljava.controller;

import org.example.traveljava.service.UserService;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.UserVO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TravelLoginController.class)
class TravelLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("登录成功：下发 access_jwt，且响应里不能带密码哈希")
    void login_shouldSetCookieAndHidePassword() throws Exception {
        UserVO stored = new UserVO(1, "alice", "HASH", "小明", null, LocalDateTime.now());
        when(userService.login("alice", "123456", "小明")).thenReturn(stored);

        mockMvc.perform(authed(post("/api/travel/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"passwordHash\":\"123456\",\"nickname\":\"小明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.passwordHash").value(nullValue()))
                .andExpect(header().string("Set-Cookie", containsString("access_jwt=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    @DisplayName("登录失败：401，且不下发 cookie")
    void login_shouldFail() throws Exception {
        when(userService.login(any(), any(), any())).thenReturn(null);

        mockMvc.perform(authed(post("/api/travel/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"passwordHash\":\"wrong\",\"nickname\":\"小明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("注册重复用户名：401")
    void register_shouldFailWhenDuplicated() throws Exception {
        when(userService.register(any())).thenReturn(null);

        mockMvc.perform(authed(post("/api/travel/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"passwordHash\":\"123456\",\"nickname\":\"小明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("当前电话号码已注册"));
    }

    @Test
    @DisplayName("注册成功：下发 cookie")
    void register_shouldSetCookie() throws Exception {
        UserVO saved = new UserVO(2, "bob", "HASH", "Bob", null, LocalDateTime.now());
        when(userService.register(any())).thenReturn(saved);

        mockMvc.perform(authed(post("/api/travel/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"passwordHash\":\"123456\",\"nickname\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string("Set-Cookie", containsString("access_jwt=")));
    }

    @Test
    @DisplayName("退出登录：把 cookie 置为立即过期")
    void logout_shouldExpireCookie() throws Exception {
        mockMvc.perform(authed(post("/api/travel/logout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("退出成功"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("修改昵称：参数透传给 Service")
    void updateNickname_shouldDelegate() throws Exception {
        when(userService.updateNickname(1, "新昵称")).thenReturn(Result.success("更新成功"));

        mockMvc.perform(authed(post("/api/travel/update_nickname")).param("userId", "1").param("nickName", "新昵称"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("更新成功"));

        verify(userService).updateNickname(1, "新昵称");
    }

    @Test
    @DisplayName("修改密码：参数透传给 Service")
    void updatePassword_shouldDelegate() throws Exception {
        when(userService.updatePassword(1, "old", "new")).thenReturn(Result.success("修改密码成功"));

        mockMvc.perform(authed(post("/api/travel/update_pwd"))
                        .param("userId", "1")
                        .param("oldPassword", "old")
                        .param("newPassword", "new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("修改密码成功"));

        verify(userService).updatePassword(1, "old", "new");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("access_jwt", JwtUtils.generateJwt(Map.of("username", "tester"))));
    }
}
