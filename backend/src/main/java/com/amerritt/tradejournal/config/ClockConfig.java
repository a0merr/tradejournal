package com.amerritt.tradejournal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * UTC, and injected rather than called statically, so "now" is both unambiguous across
     * deployments and substitutable in tests — asserting time-dependent behaviour should not
     * require sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
