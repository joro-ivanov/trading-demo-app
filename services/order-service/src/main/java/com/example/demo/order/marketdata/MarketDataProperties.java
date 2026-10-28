package com.example.demo.order.marketdata;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Connection settings for market-data-service (latest price for the funds guard and the risk check). */
@Validated
@ConfigurationProperties("market-data")
public record MarketDataProperties(@NotBlank String baseUrl,
                                   @NotNull Duration connectTimeout,
                                   @NotNull Duration readTimeout) {
}
