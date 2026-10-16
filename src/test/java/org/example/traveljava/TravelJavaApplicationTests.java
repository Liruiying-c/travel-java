package org.example.traveljava;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class TravelJavaApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testBcryptMatch(){
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = "111"; //你登录输入的明文
        String dbHash = "$2a$10$2pUEBIDb6Ppxp9mrO5oro.wLKOmDToebovaMTIeIDkYnYLMO96O5y";
        boolean result = encoder.matches(raw, dbHash);
        System.out.println(result);
    }


}
