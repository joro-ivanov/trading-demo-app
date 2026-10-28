package com.example.demo.watchlist.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response payloads for watchlist-service. */
public final class WatchlistDtos {

    /** Ticker format accepted everywhere a symbol is given. */
    public static final String SYMBOL_PATTERN = "^[A-Z]{1,10}$";

    private WatchlistDtos() {
    }

    public record CreateWatchlistRequest(@NotBlank @Size(max = 40) String name) {
    }

    public record AddSymbolRequest(@NotBlank @Pattern(regexp = SYMBOL_PATTERN) String symbol) {
    }

    /** A watchlist in the owner's list (no prices). */
    public record WatchlistSummaryResponse(UUID id, String name, int symbolCount, Instant createdAt) {
    }

    /**
     * One watchlist with its symbols. {@code pricesAvailable} is false when market-data could not be
     * reached; every {@code price} is then null.
     */
    public record WatchlistResponse(UUID id, String name, Instant createdAt, boolean pricesAvailable,
                                    List<SymbolResponse> symbols) {
    }

    /** {@code price} is null when market-data has no price for the symbol or is unavailable. */
    public record SymbolResponse(String symbol, Instant addedAt, BigDecimal price) {
    }

    /** The symbol just added to a watchlist. */
    public record AddedSymbolResponse(UUID watchlistId, String symbol, Instant addedAt) {
    }
}
