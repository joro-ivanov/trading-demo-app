package com.example.demo.portfolio.web;

import java.math.BigDecimal;

/** Current held quantity for a symbol (0 if no position). Used by order-service's short-sell guard. */
public record HoldingResponse(String symbol, BigDecimal quantity) {
}
