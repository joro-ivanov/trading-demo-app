package com.example.demo.e2e.ui;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;

import io.qameta.allure.Allure;

/**
 * Live-data + session UI flows: the streamed price ticker, the fills panel updating after an order,
 * and switching users in one browser (no stale portfolio state). The ticker is not market-hours
 * gated (the demo feed runs continuously); the order-placing flows are.
 */
@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class LiveDashboardTest extends BaseUiTest {

    @Test
    void theLiveTickerDisplaysAPrice() {
        DashboardPage dashboard = loginAsPrimaryUser();
        Allure.step("The ticker shows a streamed price for AAPL");
        dashboard.awaitTickerPrice(TradingSymbols.AAPL);
    }

    @RunDuringMarketHours
    @Test
    void aFillAppearsInTheFillsPanelAfterPlacingAnOrder() {
        DashboardPage dashboard = loginAsPrimaryUser();
        Allure.step("Place a BUY order through the dashboard");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.BUY, 1);
        Allure.step("The fill is pushed into the fills panel");
        dashboard.awaitFill();
    }

    @RunDuringMarketHours
    @Test
    void loggingInAsADifferentUserShowsADifferentPortfolio() {
        String secondUsername = registerFreshUser("u").username();

        Allure.step("Alice logs in, buys AAPL, and sees it");
        DashboardPage alice = loginAsPrimaryUser();
        alice.placeOrder(TradingSymbols.AAPL, Side.BUY, 1);
        alice.waitForPosition(TradingSymbols.AAPL);

        Allure.step("Alice logs out");
        alice.logout();

        Allure.step("Bob logs in (same browser) and sees an empty portfolio — no stale state");
        DashboardPage bob = loginAsUser(secondUsername);
        bob.awaitEmptyPortfolio();
    }
}
