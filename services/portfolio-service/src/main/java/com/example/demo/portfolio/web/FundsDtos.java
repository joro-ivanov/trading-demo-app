package com.example.demo.portfolio.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Deposit/withdraw payloads and the balance response for the funds endpoints. */
public final class FundsDtos {

    private FundsDtos() {
    }

    public record FundsRequest(@NotNull @Positive BigDecimal amount) {
    }

    public record BalanceResponse(BigDecimal cash) {
    }
}
