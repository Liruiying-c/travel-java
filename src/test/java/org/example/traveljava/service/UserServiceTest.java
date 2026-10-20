package org.example.traveljava.service;

import org.example.traveljava.Mapper.UserMapper;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录/注册最容易出的问题是"密码比对的边界"：用户不存在、密码错、昵称对不上，
 * 这三种都必须是"登录失败"，而不是 500 或者误放行。
 */
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("登录成功：用户名、昵称、密码三样都对")
    void login_shouldSucceed() {
        UserVO stored = user(1, "alice", "HASHED", "小明");
        when(userMapper.SelectByUsername("alice")).thenReturn(stored);
        when(passwordEncoder.matches("123456", "HASHED")).thenReturn(true);

        UserVO result = userService.login("alice", "123456", "小明");

        assertSame(stored, result);
    }

    @Test
    @DisplayName("用户不存在：直接失败，连密码都不用比")
    void login_shouldFailWhenUserMissing() {
        when(userMapper.SelectByUsername("nobody")).thenReturn(null);

        assertNull(userService.login("nobody", "123456", "随便"));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("密码错误：失败")
    void login_shouldFailWhenPasswordWrong() {
        when(userMapper.SelectByUsername("alice")).thenReturn(user(1, "alice", "HASHED", "小明"));
        when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

        assertNull(userService.login("alice", "wrong", "小明"));
    }

    @Test
    @DisplayName("昵称对不上：失败，且不泄露密码是否正确")
    void login_shouldFailWhenNicknameMismatch() {
        when(userMapper.SelectByUsername("alice")).thenReturn(user(1, "alice", "HASHED", "小明"));

        assertNull(userService.login("alice", "123456", "别人"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("昵称没传：当作登录失败，不能抛 NPE 变成 500")
    void login_shouldFailWhenNicknameNull() {
        when(userMapper.SelectByUsername("alice")).thenReturn(user(1, "alice", "HASHED", "小明"));

        assertNull(userService.login("alice", "123456", null));
    }

    @Test
    @DisplayName("注册成功：入库的是 BCrypt 哈希，不是明文")
    void register_shouldStoreHashedPassword() {
        UserVO input = user(null, "bob", "raw-password", "Bob");
        UserVO saved = user(2, "bob", "ENCODED", "Bob");
        when(userMapper.SelectByUsername("bob")).thenReturn(null, saved);
        when(passwordEncoder.encode("raw-password")).thenReturn("ENCODED");

        UserVO result = userService.register(input);

        assertSame(saved, result);
        ArgumentCaptor<UserVO> captor = ArgumentCaptor.forClass(UserVO.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("ENCODED", captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("注册重复用户名：失败，且不写库")
    void register_shouldRejectDuplicateUsername() {
        when(userMapper.SelectByUsername("bob")).thenReturn(user(9, "bob", "HASH", "Bob"));

        assertNull(userService.register(user(null, "bob", "raw", "Bob")));
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("改密码：旧密码错就拒绝，且不更新")
    void updatePassword_shouldRejectWrongOldPassword() {
        when(userMapper.selectPassword(1)).thenReturn("OLD_HASH");
        when(passwordEncoder.matches("wrong", "OLD_HASH")).thenReturn(false);

        Result result = userService.updatePassword(1, "wrong", "new");

        assertFalse(result.getSuccess());
        assertEquals(401, result.getCode());
        verify(userMapper, never()).updatePassword(any(), anyString());
    }

    @Test
    @DisplayName("改密码：旧密码正确则存新哈希")
    void updatePassword_shouldStoreNewHash() {
        when(userMapper.selectPassword(1)).thenReturn("OLD_HASH");
        when(passwordEncoder.matches("old", "OLD_HASH")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("NEW_HASH");

        Result result = userService.updatePassword(1, "old", "new");

        assertTrue(result.getSuccess());
        verify(userMapper).updatePassword(1, "NEW_HASH");
    }

    @Test
    @DisplayName("改昵称成功")
    void updateNickname_shouldSucceed() {
        Result result = userService.updateNickname(1, "新昵称");

        assertTrue(result.getSuccess());
        verify(userMapper).updateNickname(1, "新昵称");
    }

    @Test
    @DisplayName("改昵称失败：返回统一错误结构")
    void updateNickname_shouldReturnError() {
        when(userMapper.updateNickname(1, "x")).thenThrow(new RuntimeException("db down"));

        Result result = userService.updateNickname(1, "x");

        assertFalse(result.getSuccess());
        assertEquals("db down", result.getRawResponse());
    }

    private UserVO user(Integer id, String username, String hash, String nickname) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setPasswordHash(hash);
        vo.setNickname(nickname);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }
}
