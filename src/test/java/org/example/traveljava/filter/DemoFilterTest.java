package org.example.traveljava.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.example.traveljava.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录拦截器：放行条件写错一个字符，要么把所有人挡在门外，要么把接口全裸奔。
 * 这类逻辑最适合用 MockHttpServletRequest 直接测，不用起容器。
 */
class DemoFilterTest {

    private DemoFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DemoFilter();
    }

    @Test
    @DisplayName("登录、注册请求直接放行")
    void shouldPassLoginAndRegister() throws Exception {
        assertTrue(doFilter("/api/travel/login", null));
        assertTrue(doFilter("/api/travel/register", null));
    }

    @Test
    @DisplayName("没带 cookie：返回 NOT_LOGIN，且不进入控制器")
    void shouldRejectWithoutCookie() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean(false);

        filter.doFilter(request("/api/travel/history"), response, (req, resp) -> passed.set(true));

        assertFalse(passed.get());
        assertTrue(content(response).contains("NOT_LOGIN"));
    }

    @Test
    @DisplayName("token 非法：返回 NOT_LOGIN，且不进入控制器")
    void shouldRejectInvalidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean(false);
        MockHttpServletRequest request = request("/api/travel/history");
        request.setCookies(new Cookie("access_jwt", "not-a-jwt"));

        filter.doFilter(request, response, (req, resp) -> passed.set(true));

        assertFalse(passed.get());
        assertTrue(content(response).contains("NOT_LOGIN"));
    }

    @Test
    @DisplayName("token 合法：放行到控制器")
    void shouldPassWithValidToken() throws Exception {
        MockHttpServletRequest request = request("/api/travel/history");
        request.setCookies(new Cookie("access_jwt", JwtUtils.generateJwt(Map.of("username", "demo"))));
        AtomicBoolean passed = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (req, resp) -> passed.set(true));

        assertTrue(passed.get());
    }

    private boolean doFilter(String uri, Cookie[] cookies) throws Exception {
        MockHttpServletRequest request = request(uri);
        if (cookies != null) {
            request.setCookies(cookies);
        }
        AtomicBoolean passed = new AtomicBoolean(false);
        FilterChain chain = (req, resp) -> passed.set(true);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return passed.get();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setServerName("localhost");
        request.setServerPort(3200);
        return request;
    }

    private String content(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }
}
