package com.example.demo.marketdata.it.harness;

/** The matcher's {@code orders.resting} event (its private {@code OrderMatcher.OrderRestingEvent}). */
public record OrderRestingEvent(String orderId) {
}
