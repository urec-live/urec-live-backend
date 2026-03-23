package com.ureclive.urec_live_backend;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UrecLiveBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrecLiveBackendApplication.class, args);
    }
}
