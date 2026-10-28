package com.example.demo.order.it.harness.stubs;

import java.math.BigDecimal;

/** portfolio-service's {@code GET /funds/balance} answer ({@code FundsDtos.BalanceResponse}). */
public record BalanceResponse(BigDecimal cash) {
}
