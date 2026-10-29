package com.example.demo.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.pages.RegistrationPage;
import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.harness.RunDuringMarketHours;

import io.qameta.allure.Allure;

/** The funds panel through the live UI: a fresh user starts at zero and a deposit updates the balance. */
class FundsTest extends BaseUiTest {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final String ZERO_BALANCE_TEXT = "$0.00";
    private static final String DEPOSIT_AMOUNT = "1000";
    private static final String BALANCE_AFTER_DEPOSIT_TEXT = "$1,000.00";
    /** {@link #DEFAULT_STARTING_CASH} as the funds panel displays it. */
    private static final String STARTING_BALANCE_TEXT = "$10,000.00";
    /** The BUY fill and the SELL fill. */
    /** The funds panel shows cents. */
    private static final int DISPLAYED_SCALE = 2;

    @Test
    void depositingThroughTheFundsPanelUpdatesTheBalance() {
        String unfundedUsername = uniqueUsername();
        RegistrationPage register = openRegistrationPage();
        register.fill(unfundedUsername, emailFor(unfundedUsername), REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD)
                .submit();
        register.awaitDashboard();

        DashboardPage dashboard = dashboardPage();
        Allure.step("The funds panel shows a zero balance");
        dashboard.awaitBalance(ZERO_BALANCE_TEXT);

        Allure.step("Depositing 1000 updates the displayed balance");
        dashboard.deposit(DEPOSIT_AMOUNT);
        dashboard.awaitBalance(BALANCE_AFTER_DEPOSIT_TEXT);
    }

    @RunDuringMarketHours
    @Test
    void buyingAndSellingThroughTheDashboardUpdatesTheDisplayedBalance() {
        DashboardPage dashboard = loginAsPrimaryUser();
        dashboard.awaitBalance(STARTING_BALANCE_TEXT);
        BigDecimal startingBalance = dashboard.balanceAmount();

        Allure.step("Buying 1 AAPL updates the displayed balance by the exact filled price");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.BUY, 1);
        dashboard.waitForPosition(TradingSymbols.AAPL);
        Fill buyFill = awaitLatestFill(1, Side.BUY);
        BigDecimal preciseAfterBuy = startingBalance.subtract(buyFill.price().multiply(buyFill.quantity()));
        BigDecimal expectedAfterBuy = displayedAmount(preciseAfterBuy);
        dashboard.awaitBalance(MONEY.format(expectedAfterBuy));
        assertThat(dashboard.balanceAmount()).isEqualByComparingTo(expectedAfterBuy);

        Allure.step("Selling the same 1 AAPL updates the displayed balance by the exact filled price");
        dashboard.placeOrder(TradingSymbols.AAPL, Side.SELL, 1);
        dashboard.awaitPositionRemoved(TradingSymbols.AAPL);
        Fill sellFill = awaitLatestFill(BUY_AND_SELL_FILLS, Side.SELL);
        BigDecimal preciseAfterSell = preciseAfterBuy.add(sellFill.price().multiply(sellFill.quantity()));
        BigDecimal expectedAfterSell = displayedAmount(preciseAfterSell);
        dashboard.awaitBalance(MONEY.format(expectedAfterSell));
        assertThat(dashboard.balanceAmount()).isEqualByComparingTo(expectedAfterSell);
    }

    private Fill awaitLatestFill(int expectedCount, Side side) {
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> fills = PortfolioService.recentFills(token).asList(Fill.class);
            assertThat(fills).hasSize(expectedCount);
            Fill latest = fills.getFirst();
            assertSoftly(softly -> {
                softly.assertThat(latest.symbol()).isEqualTo(TradingSymbols.AAPL);
                softly.assertThat(latest.side()).isEqualTo(side);
                softly.assertThat(latest.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            });
        });
        return PortfolioService.recentFills(token).asList(Fill.class).getFirst();
    }

    private static BigDecimal displayedAmount(BigDecimal amount) {
        return amount.setScale(DISPLAYED_SCALE, RoundingMode.HALF_UP);
    }
}
