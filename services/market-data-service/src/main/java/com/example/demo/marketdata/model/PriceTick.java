package com.example.demo.marketdata.model;

import java.math.BigDecimal;

/** A single price update for an instrument, as received from the exchange feed. */
public record PriceTick(String symbol, BigDecimal price) {
}
