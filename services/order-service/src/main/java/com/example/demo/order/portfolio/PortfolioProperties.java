package com.example.demo.order.portfolio;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Connection settings for portfolio-service (short-sell and funds guards). */
@Validated
@ConfigurationProperties("portfolio")
public record PortfolioProperties(@NotBlank String baseUrl,
                                  @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout) {
}
