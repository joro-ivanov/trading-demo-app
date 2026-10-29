package com.example.demo.portfolio.it;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.demo.portfolio.it.harness.PortfolioITBase;

/** {@code GET /funds/balance}: the caller's cash. */
class BalanceIT extends PortfolioITBase {

    /** Seeded by the balances migration for the demo users. */
    private static final String SEEDED_DEMO_USER = "demo";
    private static final String SEEDED_DEMO_CASH = "1000000";
    private static final String OWNER_A = "scopeA";
    private static final String OWNER_B = "scopeB";
    private static final String DEPOSIT_OF_A = "700";

    @Test
    void seededDemoUserStartsWithCash() {
        assertThat(balanceCash(token(SEEDED_DEMO_USER))).isEqualByComparingTo(SEEDED_DEMO_CASH);
    }

    @Test
    void cashIsScopedPerUser() {
        String a = token(OWNER_A);
        String b = token(OWNER_B);
        deposit(a, DEPOSIT_OF_A);

        BigDecimal balanceA = balanceCash(a);
        BigDecimal balanceB = balanceCash(b);

        assertSoftly(softly -> {
            softly.assertThat(balanceA).isEqualByComparingTo(DEPOSIT_OF_A);
            softly.assertThat(balanceB).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }
}
