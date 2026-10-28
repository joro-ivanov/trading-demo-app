package com.example.demo.watchlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/** Per-owner size limits ({@code watchlists.*}). */
@Validated
@ConfigurationProperties("watchlists")
public record WatchlistLimits(@Positive int maxPerOwner, @Positive int maxSymbols) {
}
