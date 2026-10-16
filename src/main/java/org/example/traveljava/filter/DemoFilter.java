package org.example.traveljava.filter;

import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.utils.JwtUtils;
import org.example.traveljava.vo.Result;
import org.springframework.util.StringUtils;

import java.io.IOException;

@WebFilter(urlPatterns = "/*")
public class DemoFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 1.获取请求url
        String url = req.getRequestURL().toString();
        System.out.println("请求的url:{}");

        // 2.判断请求url中是否包含login,如果包含，说明是登录操作，放行
        if (url.contains("login") || url.contains("login")) {
            System.out.println("登录操作，放行...");
            chain.doFilter(request, response);
            return;
        }

        // 3.从Cookie获取令牌(token)
        String jwt = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_jwt".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

// 4.判断令牌是否存在，如果不存在，返回错误结果(未登录)
        if (!StringUtils.hasLength(jwt)) {
            System.out.println("Cookie中token为空，返回未登录信息");
            Result error = Result.error("NOT_LOGIN", "");
            // 手动转换 对象--json  ----> 阿里巴巴fastJSON
            String notLogin = JSONObject.toJSONString(error);
            resp.getWriter().write(notLogin);
            return;
        }

        // 5. 解析token,如果解析失败，返回错误结果（未登录）
        try {
            JwtUtils.ParseJwt(jwt);
        } catch (Exception e) { // jwt解析失败
            e.printStackTrace();
            System.out.println("解析令牌失败，返回未登录错误信息");
            Result error = Result.error("NOT_LOGIN", e.getMessage());
            String notLogin = JSONObject.toJSONString(error);
            resp.getWriter().write(notLogin);
            return;
        }


        // 6. 放行
        System.out.println("令牌合法，放行");
        chain.doFilter(request, response);
    }
}
