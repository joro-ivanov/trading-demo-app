package com.example.demo.watchlist.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One symbol on a watchlist and when it was added. */
@Embeddable
public record WatchlistSymbol(
        @Column(name = "symbol", nullable = false, length = 10) String symbol,
        @Column(name = "added_at", nullable = false) Instant addedAt) {
}
