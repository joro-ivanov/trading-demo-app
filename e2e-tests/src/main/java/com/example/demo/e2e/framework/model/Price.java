package com.example.demo.e2e.framework.model;

import java.math.BigDecimal;

/** Latest price for an instrument, returned by the market-data-service. */
public record Price(String symbol, BigDecimal price) {
}
