package com.example.demo.portfolio.events;

import java.math.BigDecimal;

/** A price tick consumed from the {@code price.ticks} topic. */
public record PriceMessage(String symbol, BigDecimal price) {
}
