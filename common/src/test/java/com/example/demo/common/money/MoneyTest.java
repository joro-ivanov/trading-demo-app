package com.example.demo.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Test
    void addsExactlyAtFixedScale() {
        assertThat(Money.of("0.1").add(Money.of("0.2")).amount()).isEqualByComparingTo("0.3");
    }

    @Test
    void multipliesByAQuantity() {
        assertThat(Money.of("190.40").multiply(new BigDecimal("10")).amount()).isEqualByComparingTo("1904.00");
    }

    @Test
    void dividesWithBankersRounding() {
        assertThat(Money.of("10").divide(new BigDecimal("3")).amount()).isEqualByComparingTo("3.3333");
    }

    @Test
    void equalityIgnoresInputScale() {
        assertThat(Money.of("5")).isEqualTo(Money.of("5.0000"));
    }

    @Test
    void subtractsExactly() {
        assertThat(Money.of("190.40").subtract(Money.of("10.15")).amount()).isEqualByComparingTo("180.25");
    }

    @Test
    void subtractCanGoNegative() {
        assertThat(Money.of("10").subtract(Money.of("25")).amount()).isEqualByComparingTo("-15");
    }

    @Test
    void comparesByAmount() {
        assertSoftly(softly -> {
            softly.assertThat(Money.of("100").compareTo(Money.of("90"))).isPositive();
            softly.assertThat(Money.of("90").compareTo(Money.of("100"))).isNegative();
            softly.assertThat(Money.of("50").compareTo(Money.of("50.0000"))).isZero();
        });
    }

    // Banker's rounding (HALF_EVEN) at scale 4: a .5 in the 5th place rounds to the nearest EVEN digit.
    @ParameterizedTest(name = "{0} rounds to {1}")
    @CsvSource({"0.00005,0.0000", "0.00015,0.0002", "0.00025,0.0002", "0.00035,0.0004"})
    void roundsHalfToEven(String input, String expected) {
        assertThat(Money.of(input).amount()).isEqualByComparingTo(expected);
    }

    @Test
    void toStringIsPlain() {
        assertThat(Money.of("1904.00").toString()).isEqualTo("1904.0000");
    }
}
