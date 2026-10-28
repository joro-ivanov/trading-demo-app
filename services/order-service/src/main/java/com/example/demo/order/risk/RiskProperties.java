package com.example.demo.order.risk;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * risk-service connection settings. {@code enabled=false} skips the pre-trade risk check entirely
 * (used by the cross-service flagship, which does not boot risk-service).
 */
@Validated
@ConfigurationProperties("risk")
public record RiskProperties(boolean enabled,
                             @NotBlank String baseUrl,
                             @NotNull Duration connectTimeout,
                             @NotNull Duration readTimeout) {
}
