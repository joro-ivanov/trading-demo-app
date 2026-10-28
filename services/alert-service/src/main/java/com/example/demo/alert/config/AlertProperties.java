package com.example.demo.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/** Business limits for alerts. */
@Validated
@ConfigurationProperties("alerts")
public record AlertProperties(@Positive int maxActivePerOwner) {
}
