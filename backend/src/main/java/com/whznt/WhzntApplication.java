package com.whznt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.whznt.mapper")
@SpringBootApplication
public class WhzntApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhzntApplication.class, args);
    }
}
