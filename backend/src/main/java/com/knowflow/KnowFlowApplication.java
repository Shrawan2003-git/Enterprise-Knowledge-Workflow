package com.knowflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowFlowApplication.class, args);
    }
}
