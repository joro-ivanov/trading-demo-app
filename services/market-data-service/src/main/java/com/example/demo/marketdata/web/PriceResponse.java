package com.example.demo.marketdata.web;

import java.math.BigDecimal;

/** Latest price for an instrument. */
public record PriceResponse(String symbol, BigDecimal price) {
}
