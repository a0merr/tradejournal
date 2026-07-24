package com.amerritt.tradejournal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling drives one job: sweeping expired refresh tokens (RefreshTokenCleanupJob).
@SpringBootApplication
@EnableScheduling
public class TradejournalApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradejournalApplication.class, args);
    }
}
