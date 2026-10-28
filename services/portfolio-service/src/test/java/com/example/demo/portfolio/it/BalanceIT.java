package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;

/** {@code GET /funds/balance}: the caller's cash. */
class BalanceIT extends PortfolioITBase {

    @Test
    void seededDemoUserStartsWithCash() {
        assertThat(balanceCash(token("demo"))).isEqualByComparingTo("1000000");
    }

    @Test
    void cashIsScopedPerUser() {
        String a = token("scopeA");
        String b = token("scopeB");
        deposit(a, "700");

        BigDecimal balanceA = balanceCash(a);
        BigDecimal balanceB = balanceCash(b);

        assertSoftly(softly -> {
            softly.assertThat(balanceA).isEqualByComparingTo("700");
            softly.assertThat(balanceB).isEqualByComparingTo("0");
        });
    }
}
