package com.example.demo.order.it.harness.stubs;

/** The exchange's routing acknowledgement, as in {@code infra/wiremock/mappings/exchange-orders.json}. */
public record ExchangeAck(String status, String exchangeOrderId) {
}
