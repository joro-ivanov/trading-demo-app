package com.example.demo.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    // Exact decimal addition (no binary floating-point drift).
    private static final String TENTH = "0.1";
    private static final String TWO_TENTHS = "0.2";
    private static final String THREE_TENTHS = "0.3";

    private static final String UNIT_PRICE = "190.40";
    private static final String QUANTITY = "10";
    private static final String UNIT_PRICE_TIMES_QUANTITY = "1904.00";
    private static final String UNIT_PRICE_AT_SCALE_4 = "1904.0000";

    private static final String DIVIDEND = "10";
    private static final String DIVISOR = "3";
    private static final String QUOTIENT_AT_SCALE_4 = "3.3333";

    private static final String AMOUNT = "5";
    private static final String SAME_AMOUNT_AT_SCALE_4 = "5.0000";

    private static final String DEDUCTION = "10.15";
    private static final String PRICE_MINUS_DEDUCTION = "180.25";

    private static final String MINUEND = "10";
    private static final String LARGER_SUBTRAHEND = "25";
    private static final String NEGATIVE_DIFFERENCE = "-15";

    private static final String HIGHER = "100";
    private static final String LOWER = "90";
    private static final String EQUAL = "50";
    private static final String EQUAL_AT_SCALE_4 = "50.0000";
    /** {@code Integer.signum} of a {@code compareTo} result: exactly one of LESS, 0 or GREATER. */
    private static final int GREATER = 1;
    private static final int LESS = -1;

    @Test
    void addsExactlyAtFixedScale() {
        assertThat(Money.of(TENTH).add(Money.of(TWO_TENTHS)).amount()).isEqualByComparingTo(THREE_TENTHS);
    }

    @Test
    void multipliesByAQuantity() {
        assertThat(Money.of(UNIT_PRICE).multiply(new BigDecimal(QUANTITY)).amount())
                .isEqualByComparingTo(UNIT_PRICE_TIMES_QUANTITY);
    }

    @Test
    void dividesWithBankersRounding() {
        assertThat(Money.of(DIVIDEND).divide(new BigDecimal(DIVISOR)).amount()).isEqualByComparingTo(QUOTIENT_AT_SCALE_4);
    }

    @Test
    void equalityIgnoresInputScale() {
        assertThat(Money.of(AMOUNT)).isEqualTo(Money.of(SAME_AMOUNT_AT_SCALE_4));
    }

    @Test
    void subtractsExactly() {
        assertThat(Money.of(UNIT_PRICE).subtract(Money.of(DEDUCTION)).amount()).isEqualByComparingTo(PRICE_MINUS_DEDUCTION);
    }

    @Test
    void subtractCanGoNegative() {
        assertThat(Money.of(MINUEND).subtract(Money.of(LARGER_SUBTRAHEND)).amount())
                .isEqualByComparingTo(NEGATIVE_DIFFERENCE);
    }

    @Test
    void comparesByAmount() {
        assertSoftly(softly -> {
            softly.assertThat(Integer.signum(Money.of(HIGHER).compareTo(Money.of(LOWER)))).isEqualTo(GREATER);
            softly.assertThat(Integer.signum(Money.of(LOWER).compareTo(Money.of(HIGHER)))).isEqualTo(LESS);
            softly.assertThat(Money.of(EQUAL).compareTo(Money.of(EQUAL_AT_SCALE_4))).isZero();
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
        assertThat(Money.of(UNIT_PRICE_TIMES_QUANTITY).toString()).isEqualTo(UNIT_PRICE_AT_SCALE_4);
    }
}
