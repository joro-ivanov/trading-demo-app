package com.example.demo.order.exchange;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Connection settings for the exchange the order is routed to. */
@Validated
@ConfigurationProperties("exchange")
public record ExchangeProperties(@NotBlank String baseUrl,
                                 @NotNull Duration connectTimeout,
                                 @NotNull Duration readTimeout) {
}
