package org.example.traveljava.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 是最容易被"只测正常路径"放过的部分：真正要保证的是篡改和过期必须被拒。
 */
class JwtUtilsTest {

    @Test
    @DisplayName("签发后能解析，claims 原样带回来")
    void shouldRoundTripClaims() {
        String jwt = JwtUtils.generateJwt(Map.of("username", "13800000000", "password", "hash"));

        assertEquals(3, jwt.split("\\.").length, "JWT 应该是 header.payload.signature 三段");

        Claims claims = JwtUtils.ParseJwt(jwt);
        assertEquals("13800000000", claims.get("username"));
        assertEquals("hash", claims.get("password"));
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    @DisplayName("篡改过的 token 必须解析失败")
    void shouldRejectTamperedToken() {
        String jwt = JwtUtils.generateJwt(Map.of("username", "demo"));
        String tampered = jwt.substring(0, jwt.length() - 2) + "xx";

        assertThrows(Exception.class, () -> JwtUtils.ParseJwt(tampered));
    }

    @Test
    @DisplayName("过期的 token 必须解析失败")
    void shouldRejectExpiredToken() {
        // jjwt 会往 claims 里写 exp，这里必须给可变 Map
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "demo");
        String expired = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, "itiheima")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .compact();

        assertThrows(Exception.class, () -> JwtUtils.ParseJwt(expired));
    }
}
