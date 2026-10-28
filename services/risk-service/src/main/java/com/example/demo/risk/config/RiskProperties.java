package com.example.demo.risk.config;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Risk limit configuration: the {@code defaults} applied to users without their own limits, and the
 * {@code maxAllowed} upper bounds a user may set.
 */
@Validated
@ConfigurationProperties("risk")
public record RiskProperties(@Valid @NotNull Limits defaults, @Valid @NotNull Limits maxAllowed) {

    public record Limits(@NotNull @Positive BigDecimal maxOrderNotional,
                         @NotNull @Positive BigDecimal maxPositionQuantity) {
    }
}
