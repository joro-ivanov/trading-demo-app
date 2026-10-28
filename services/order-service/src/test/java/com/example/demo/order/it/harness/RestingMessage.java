package com.example.demo.order.it.harness;

/** An {@code orders.resting} record as market-data-service publishes it. */
public record RestingMessage(String orderId) {
}
