package com.recovermandate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecoverMandateApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecoverMandateApplication.class, args);
    }
}
// /...