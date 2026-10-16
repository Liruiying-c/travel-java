package org.example.traveljava.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.Map;

public class JwtUtils {
    static public String generateJwt(Map<String, Object> claims) {
        String jwt = Jwts.builder()
                .setClaims(claims) //自定义内容（载荷）
                .signWith(SignatureAlgorithm.HS256,"itiheima") //签名算法
                .setExpiration(new Date(System.currentTimeMillis() + 12 * 3600 * 1000)) //有效期12h
                .compact();

        return jwt;
    }

    static public Claims ParseJwt(String jwt){
        Claims claims = Jwts.parser()
                .setSigningKey("itiheima")
                .parseClaimsJws(jwt)
                .getBody();

        return claims;
    }
}
