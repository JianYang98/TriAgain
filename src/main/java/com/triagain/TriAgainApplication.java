package com.triagain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TriAgainApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriAgainApplication.class, args);
    }
}
