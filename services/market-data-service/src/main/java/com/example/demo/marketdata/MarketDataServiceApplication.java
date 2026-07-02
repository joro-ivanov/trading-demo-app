package com.example.demo.marketdata;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;

import com.example.demo.common.security.ApiSecurityConfig;
import com.example.demo.common.time.Clocks;

@SpringBootApplication
@EnableScheduling
@Import(ApiSecurityConfig.class)
public class MarketDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataServiceApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    Clock clock(@Value("${app.clock.fixed-instant:}") String fixedInstant) {
        return Clocks.systemUtcOrFixed(fixedInstant);
    }
}
