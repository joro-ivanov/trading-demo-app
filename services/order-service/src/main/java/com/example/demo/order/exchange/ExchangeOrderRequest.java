package com.example.demo.order.exchange;

import java.math.BigDecimal;

/** Payload routed to the (mocked) upstream exchange. */
public record ExchangeOrderRequest(String symbol, String side, BigDecimal quantity) {
}
