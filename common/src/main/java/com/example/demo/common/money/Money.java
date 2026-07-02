package com.example.demo.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Fixed-point monetary value (docs/DESIGN.md §10): a {@link BigDecimal} amount normalized to a
 * fixed scale with banker's rounding ({@code HALF_EVEN}). Never use {@code double} for money. JSON
 * and persistence happen at the edges as {@link BigDecimal} (via {@link #amount()} / {@link #of}),
 * so wire formats are unchanged while all arithmetic stays exact.
 */
public final class Money implements Comparable<Money> {

    public static final int SCALE = 4;
    public static final Money ZERO = Money.of(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public BigDecimal amount() {
        return amount;
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /** Multiply by a non-monetary factor (e.g. a share quantity) to get a notional amount. */
    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public Money divide(BigDecimal divisor) {
        return new Money(amount.divide(divisor, SCALE, RoundingMode.HALF_EVEN));
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && amount.equals(money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
