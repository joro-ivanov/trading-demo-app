package com.example.demo.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.ui.PlaywrightManager;
import com.example.demo.e2e.harness.RunDuringMarketHours;

import io.qameta.allure.Allure;

/**
 * Per-user portfolio isolation in the UI, driven through TWO simultaneous, isolated browser contexts
 * (alice and bob). Each Page Object binds to the context it was built in, so the two sessions run
 * independently. Proves a user only ever sees their own book (the M21 per-user change end-to-end).
 */
class MultiUserPortfolioTest extends BaseUiTest {

    private static final int ORDER_QUANTITY = 5;

    @RunDuringMarketHours
    @Test
    void twoUsersSeeOnlyTheirOwnPortfolio() {
        String secondUsername = registerFreshUser(USERNAME_PREFIX).username();

        Allure.step("Alice logs in (context 1) and buys AAPL through the dashboard");
        DashboardPage alice = loginAsPrimaryUser();
        alice.placeOrder(TradingSymbols.AAPL, Side.BUY, ORDER_QUANTITY);
        alice.waitForPosition(TradingSymbols.AAPL);

        Allure.step("Bob logs in in a second, isolated browser context");
        PlaywrightManager.openSecondContext();
        navigateToBaseUrl();
        DashboardPage bob = loginAsUser(secondUsername);

        Allure.step("Bob's portfolio is empty — he does not see Alice's AAPL position");
        bob.awaitEmptyPortfolio();
        assertThat(bob.hasPosition(TradingSymbols.AAPL)).isFalse();

        Allure.step("Alice still sees her own AAPL position (context 1 is unaffected)");
        assertThat(alice.hasPosition(TradingSymbols.AAPL)).isTrue();
    }
}
