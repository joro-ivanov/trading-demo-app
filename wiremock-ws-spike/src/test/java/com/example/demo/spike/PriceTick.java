package com.example.demo.spike;

import java.math.BigDecimal;

/** The exchange feed's price frame, mirroring market-data-service's {@code model.PriceTick}. */
record PriceTick(String symbol, BigDecimal price) {
}
