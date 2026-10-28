package com.example.demo.order.it.harness;

import java.math.BigDecimal;

/** A MARKET order body that also claims an {@code owner}, which the service must ignore. */
public record OwnerClaimingOrderRequest(String symbol, String side, BigDecimal quantity, String owner) {
}
