package com.example.demo.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.demo.e2e.framework.model.Position;
import com.example.demo.e2e.framework.model.Price;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.pages.OrderTicketPage;
import com.example.demo.e2e.framework.service.marketdata.MarketDataService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.framework.support.OrderFactory;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;

import io.qameta.allure.Allure;

/**
 * UI e2e (docs/DESIGN.md §7.2, §8.6). Drives the real dashboard in a browser via Page Objects,
 * then cross-checks the result through a service object. Runs against the docker-compose stack
 * (skipped if it is not up). Tagged {@code ui} so it is excluded from the default {@code verify}
 * and run via {@code mvn -Pui-e2e verify}. Order placement is gated to market hours.
 */
@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class TradingTest extends BaseUiTest {

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY MARKET {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyMarketOrderThroughTheDashboard(String symbol) {
        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Place a marketable MARKET BUY 10 " + symbol + " via the ticket");
        dashboard.placeOrder(symbol, Side.BUY, 10, OrderType.MARKET, "0");

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        dashboard.waitForPosition(symbol);
        Position position = position(symbol);
        assertSoftly(softly -> {
            softly.assertThat(position.symbol()).isEqualTo(symbol);
            softly.assertThat(position.quantity()).isEqualByComparingTo("10");
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL MARKET {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellMarketOrderThroughTheDashboard(String symbol) {
        Allure.step("Seed a position in " + symbol + " (via the API) so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Place a marketable MARKET SELL 10 " + symbol + " via the ticket");
        dashboard.placeOrder(symbol, Side.SELL, 10, OrderType.MARKET, "0");

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.symbol()).isEqualTo(symbol);
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
            });
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY LIMIT {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyLimitOrderThroughTheDashboard(String symbol) {
        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Read the latest " + symbol + " price; a limit 10% above it is immediately marketable");
        BigDecimal limitPrice = OrderFactory.marketableBuyLimit(
                MarketDataService.latestPrice(token, symbol).as(Price.class).price());

        Allure.step("Place a marketable LIMIT BUY 10 " + symbol + " @ " + limitPrice + " via the ticket");
        dashboard.placeOrder(symbol, Side.BUY, 10, OrderType.LIMIT, limitPrice.toPlainString());

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        dashboard.waitForPosition(symbol);
        Position position = position(symbol);
        assertSoftly(softly -> {
            softly.assertThat(position.symbol()).isEqualTo(symbol);
            softly.assertThat(position.quantity()).isEqualByComparingTo("10");
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL LIMIT {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellLimitOrderThroughTheDashboard(String symbol) {
        Allure.step("Seed a position in " + symbol + " (via the API) so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Place a marketable LIMIT SELL 10 " + symbol + " via the ticket");
        dashboard.placeOrder(symbol, Side.SELL, 10, OrderType.LIMIT, "0.01");

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.symbol()).isEqualTo(symbol);
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
            });
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY STOP {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyStopOrderThroughTheDashboard(String symbol) {
        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Place a marketable STOP BUY 10 " + symbol + " via the ticket");
        dashboard.placeOrder(symbol, Side.BUY, 10, OrderType.STOP, "0.01");

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        dashboard.waitForPosition(symbol);
        Position position = position(symbol);
        assertSoftly(softly -> {
            softly.assertThat(position.symbol()).isEqualTo(symbol);
            softly.assertThat(position.quantity()).isEqualByComparingTo("10");
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL STOP {0} through the dashboard")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellStopOrderThroughTheDashboard(String symbol) {
        Allure.step("Seed a position in " + symbol + " (via the API) so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();

        Allure.step("Read the latest " + symbol + " price; a SELL stop 10% above it is already breached");
        BigDecimal stopPrice = OrderFactory.marketableSellStop(
                MarketDataService.latestPrice(token, symbol).as(Price.class).price());

        Allure.step("Place a marketable STOP SELL 10 " + symbol + " @ " + stopPrice + " via the ticket");
        dashboard.placeOrder(symbol, Side.SELL, 10, OrderType.STOP, stopPrice.toPlainString());

        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.symbol()).isEqualTo(symbol);
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
            });
        });
    }

    // A non-marketable LIMIT/STOP (limit far below market for BUY; stop far above) is a valid order —
    // the ticket accepts it (no error) — but it never fills (no resting book), so the position is
    // unchanged. Pins the "accepted but pending" path through the real UI + matcher.
    @RunDuringMarketHours
    @ParameterizedTest(name = "{0} BUY GOOG @ {1} is accepted but stays pending")
    @MethodSource("pendingTypedOrders")
    void pendingTypedOrderIsAcceptedButDoesNotFill(OrderType orderType, String price) {
        Allure.step("Open the dashboard and log in");
        DashboardPage dashboard = loginAsPrimaryUser();
        OrderTicketPage ticket = orderTicketPage();
        BigDecimal before = googQuantity();

        Allure.step("Place a non-marketable " + orderType + " BUY 10 GOOG @ " + price);
        dashboard.placeOrder(TradingSymbols.GOOG, Side.BUY, 10, orderType, price);

        Allure.step("The order is accepted (no error) — valid, just not marketable");
        ticket.awaitButtonIdle();
        assertThat(ticket.hasOrderError()).isFalse();

        Allure.step("It never fills: the GOOG position is unchanged");
        await().pollDelay(STABILITY_POLL_DELAY).atMost(STABILITY_TIMEOUT).untilAsserted(() ->
                assertThat(googQuantity()).isEqualByComparingTo(before));
    }

    private BigDecimal googQuantity() {
        return PortfolioService.positions(token).asList(Position.class).stream()
                .filter(position -> position.symbol().equals(TradingSymbols.GOOG))
                .map(Position::quantity)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private static Stream<Arguments> pendingTypedOrders() {
        return Stream.of(
                Arguments.of(OrderType.LIMIT, "0.01"),
                // 10x the demo prices: never reached by the feed (non-marketable), notional within risk limits
                Arguments.of(OrderType.STOP, "1000"));
    }

    private Position position(String symbol) {
        return PortfolioService.positions(token).asList(Position.class).stream()
                .filter(position -> position.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + symbol));
    }
}
