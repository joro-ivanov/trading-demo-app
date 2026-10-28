package com.example.demo.risk;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.example.demo.common.security.ApiSecurityConfig;
import com.example.demo.common.time.Clocks;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(ApiSecurityConfig.class)
public class RiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }

    @Bean
    Clock clock(@Value("${app.clock.fixed-instant:}") String fixedInstant) {
        return Clocks.systemUtcOrFixed(fixedInstant);
    }
}
