package com.example.demo.risk.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Connection settings for portfolio-service (holdings lookup). */
@Validated
@ConfigurationProperties("portfolio")
public record PortfolioProperties(@NotBlank String baseUrl,
                                  @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout) {
}
