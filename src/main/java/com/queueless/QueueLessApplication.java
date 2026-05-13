package com.queueless;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * QueueLess Application Entry Point.
 * Digital token and appointment management for local Indian shops.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class QueueLessApplication {


    public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(QueueLessApplication.class, args);
    }
}