package com.example.demo.testing.support.contracts;

import java.math.BigDecimal;

/** A {@code price.ticks} message / exchange feed frame exactly as market-data's {@code model/PriceTick}. */
public record PriceTick(String symbol, BigDecimal price) {
}
