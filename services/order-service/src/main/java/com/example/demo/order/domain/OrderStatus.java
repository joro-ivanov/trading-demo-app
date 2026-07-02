package com.example.demo.order.domain;

/**
 * Order lifecycle: NEW (just created) → SUBMITTED (accepted by the exchange, awaiting the matcher) →
 * FILLED (a fill arrived) or PENDING (the matcher reported it non-marketable / resting). REJECTED if
 * the exchange declined; CANCELLED if the owner cancelled it.
 */
public enum OrderStatus {
    NEW, SUBMITTED, PENDING, FILLED, REJECTED, CANCELLED
}
