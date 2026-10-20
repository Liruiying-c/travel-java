package org.example.traveljava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
public class TravelJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelJavaApplication.class, args);
    }

}
