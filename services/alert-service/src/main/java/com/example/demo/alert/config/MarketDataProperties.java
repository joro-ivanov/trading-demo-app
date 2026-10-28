package com.example.demo.alert.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Connection settings for market-data-service (symbol validation). */
@Validated
@ConfigurationProperties("market-data")
public record MarketDataProperties(@NotBlank String baseUrl,
                                   @NotNull Duration connectTimeout,
                                   @NotNull Duration readTimeout) {
}
