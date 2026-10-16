package org.example.traveljava;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.example.traveljava.Mapper")
public class TravelJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelJavaApplication.class, args);
    }

}
