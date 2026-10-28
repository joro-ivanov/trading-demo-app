package com.example.demo.notification.it.harness;

import java.math.BigDecimal;

/** A {@code price.ticks} message exactly as market-data publishes it (market-data {@code model/PriceTick}). */
public record PriceTick(String symbol, BigDecimal price) {
}
