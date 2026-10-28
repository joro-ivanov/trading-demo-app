package com.example.demo.e2e.ui;

import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.OK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.pages.LoginPage;
import com.example.demo.e2e.framework.service.auth.AuthService;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;

import io.qameta.allure.Allure;

@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class PortfolioLifecycleTest extends BaseUiTest {

    private static final String USER_PREFIX = "portfolio";
    /** The BUY fill and the SELL fill. */
    private static final int BUY_AND_SELL_FILLS = 2;

    @RunDuringMarketHours
    @Test
    void closePositionRemovesRowAndRecentFillsPersistAcrossRelogin() {
        String username = uniqueUsername(USER_PREFIX);
        String email = emailFor(username);

        Allure.step("Register and fund a fresh user through the API");
        ApiResponse registrationResponse = AuthService.register(username, email, REGISTERED_USER_PASSWORD);
        Login registration = registrationResponse.as(Login.class);
        ApiResponse depositResponse = FundsService.deposit(registration.token(), DEFAULT_STARTING_CASH);
        assertSoftly(softly -> {
            softly.assertThat(registrationResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(registration.username()).isEqualTo(username);
            softly.assertThat(depositResponse.statusCode()).isEqualTo(OK);
        });

        Allure.step("Log in through the UI and open a fresh dashboard session");
        DashboardPage dashboard = openLoginPage().login(username, REGISTERED_USER_PASSWORD);

        Allure.step("Buy 1 AAPL and wait for the position and recent fill to appear");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.BUY, 1);
        dashboard.waitForPosition(TradingSymbols.AAPL);
        dashboard.awaitFill();

        Allure.step("Sell 1 AAPL and verify the portfolio row is removed");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.SELL, 1);
        dashboard.awaitFillCount(BUY_AND_SELL_FILLS);
        dashboard.awaitPositionRemoved(TradingSymbols.AAPL);
        String firstFillBeforeRelogin = dashboard.firstFillText();

        Allure.step("The persisted recent fills endpoint reports both fills with timestamps");
        List<Fill> fillsBeforeRelogin = PortfolioService.recentFills(registration.token()).asList(Fill.class);
        assertSoftly(softly -> {
            softly.assertThat(fillsBeforeRelogin).hasSizeGreaterThanOrEqualTo(BUY_AND_SELL_FILLS);
            softly.assertThat(fillsBeforeRelogin).allSatisfy(fill -> assertThat(fill.filledAt()).isNotNull());
        });

        Allure.step("Log out and log back in, then verify the same recent fill is still shown");
        dashboard.logout();
        LoginPage loginPage = openLoginPage();
        loginPage.awaitShown();
        DashboardPage reloggedDashboard = loginPage.login(username, REGISTERED_USER_PASSWORD);
        reloggedDashboard.awaitFill();
        assertThat(reloggedDashboard.firstFillText()).isEqualTo(firstFillBeforeRelogin);

        Allure.step("The closed AAPL position is still absent after relogin");
        assertThat(reloggedDashboard.hasPosition(TradingSymbols.AAPL)).isFalse();
    }
}
