package com.example.demo.e2e.api;

import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.ErrorResponse;
import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderResponse;
import com.example.demo.e2e.framework.model.OrderStatus;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Position;
import com.example.demo.e2e.framework.model.Price;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.service.marketdata.MarketDataService;
import com.example.demo.e2e.framework.service.order.OrderHistoryService;
import com.example.demo.e2e.framework.service.order.OrdersService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.framework.support.OrderFactory;
import com.example.demo.e2e.framework.ws.NotificationsWsService;
import com.example.demo.e2e.framework.ws.WsSession;
import com.example.demo.e2e.harness.RequireRunningBackend;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

/**
 * API + WebSocket e2e suite (docs/DESIGN.md §7.2, §8). Black-box: drives an already-running stack
 * (locally {@code docker compose up}; in CI a deployed environment) through the facade service
 * objects only, narrating each step with {@code Allure.step(...)}. It never boots anything.
 *
 * <p>Assertions are robust against a live environment: deterministic facts are asserted exactly
 * (the order echoes the request; a fill references the placed order id), while values the test does
 * not control (live prices) are only asserted to be present. Skipped if the stack is not up.
 */
@Tag("api")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(TestLogArtifacts.class)
class TradingTest extends BaseApiTest {

    private static final String LIMIT_PRICE_FIELD = "limitPrice";
    private static final String STOP_PRICE_FIELD = "stopPrice";

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY MARKET {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyMarketOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Place a marketable MARKET BUY order for 10 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("10"));
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The " + symbol + " position appears in the portfolio");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.symbol()).isEqualTo(symbol);
                softly.assertThat(position.quantity()).isEqualByComparingTo("10");
            });
        });

        Allure.step("The order is resolved to FILLED (the matcher filled it and order-service consumed it)");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL MARKET {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellMarketOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Seed a position in " + symbol + " so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = findPositionBySymbol(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Place a marketable MARKET SELL order for 10 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, Side.SELL, new BigDecimal("10"));
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The " + symbol + " position remains represented in the portfolio");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertThat(position.quantity()).isEqualByComparingTo("10");
        });

        Allure.step("The order is resolved to FILLED (the matcher filled it and order-service consumed it)");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY LIMIT {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyLimitOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Place a marketable LIMIT BUY order for 10 " + symbol);
        OrderRequest limitOrder = OrderFactory.limitBuy(symbol, 10, new BigDecimal("100000"));
        ApiResponse response = OrdersService.placeOrder(token, limitOrder);
        OrderResponse orderResponse = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(orderResponse.id()).isNotBlank();
            softly.assertThat(orderResponse.symbol()).isEqualTo(symbol);
            softly.assertThat(orderResponse.side()).isEqualTo(Side.BUY);
            softly.assertThat(orderResponse.quantity()).isEqualByComparingTo("10");
            softly.assertThat(orderResponse.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(orderResponse.limitPrice()).isEqualByComparingTo("100000");
            softly.assertThat(orderResponse.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = orderResponse.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertThat(position.quantity()).isEqualByComparingTo("10");
        });
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL LIMIT {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellLimitOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Seed a position in " + symbol + " so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = findPositionBySymbol(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Place a marketable LIMIT SELL order for 10 " + symbol);
        OrderRequest limitSellOrderRequest = OrderFactory.limitSell(symbol, 10, new BigDecimal("0.01"));
        ApiResponse response = OrdersService.placeOrder(token, limitSellOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(order.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertThat(position.quantity()).isEqualByComparingTo("10");
        });
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY STOP {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyStopOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Place a marketable STOP BUY order for 10 " + symbol);
        OrderRequest stopBuyOrderRequest = OrderFactory.stopBuy(symbol, 10, new BigDecimal("0.01"));
        ApiResponse response = OrdersService.placeOrder(token, stopBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.STOP);
            softly.assertThat(order.stopPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertThat(position.quantity()).isEqualByComparingTo("10");
        });
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @RunDuringMarketHours
    @ParameterizedTest(name = "SELL STOP {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingASellStopOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Seed a position in " + symbol + " so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("20"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = findPositionBySymbol(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("20");
        });

        Allure.step("Place a marketable STOP SELL order for 10 " + symbol);
        OrderRequest stopSellOrderRequest = OrderFactory.stopSell(symbol, 10, new BigDecimal("1000000"));
        ApiResponse response = OrdersService.placeOrder(token, stopSellOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.STOP);
            softly.assertThat(order.stopPrice()).isEqualByComparingTo("1000000");
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = findPositionBySymbol(symbol);
            assertThat(position.quantity()).isEqualByComparingTo("10");
        });
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @Test
    void latestPriceIsServed() {
        Allure.step("market-data serves the latest AAPL price");
        ApiResponse response = MarketDataService.latestPrice(token, TradingSymbols.AAPL);
        Price price = response.as(Price.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(price.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(price.price()).isNotNull();
            softly.assertThat(price.price()).isPositive();
        });
    }

    @Test
    void invalidOrderIsRejectedWith400() {
        Allure.step("Submitting a blank symbol returns 400 (no client-side validation)");
        ApiResponse response = OrdersService.placeMarketOrder(token, "", Side.BUY, new BigDecimal("-5"));
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(problem.error()).isEqualTo("Bad Request");
            softly.assertThat(problem.status()).isEqualTo(400);
            softly.assertThat(problem.message()).isEqualTo("Invalid request content.");
            softly.assertThat(problem.path()).isEqualTo("/orders");
        });
    }

    // Both sides exercised end-to-end: this proves the order→match→fill→WS-push chain carries a SELL
    // as well as a BUY. The SELL position arithmetic itself is owned by the portfolio-service FillsListenerIT (no overlap).
    @RunDuringMarketHours
    @ParameterizedTest(name = "{0} fill for {1} is pushed over WebSocket")
    @MethodSource("webSocketFillOrders")
    void fillIsPushedOverWebSocketForThePlacedOrder(Side side, String symbol) {
        Allure.step("Seed a position in " + symbol + " so a SELL passes the short-sell guard");
        OrdersService.placeMarketOrder(token, symbol, Side.BUY, new BigDecimal("10"));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = findPositionBySymbol(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo("10");
        });

        Allure.step("Subscribe to fills over WebSocket (STOMP)");
        WsSession session = NotificationsWsService.subscribeFills(token);

        Allure.step("Place a " + side + " order for 5 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, side, new BigDecimal("5"));
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(side);
            softly.assertThat(order.quantity()).isEqualByComparingTo("5");
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The fill for that order is pushed to the subscribed client");
        Fill frameFill = session.nextFrameAs(Fill.class, SHORT_WS_FRAME_TIMEOUT);
        session.close();
        assertSoftly(softly -> {
            softly.assertThat(frameFill.orderId()).isEqualTo(orderId);
            softly.assertThat(frameFill.symbol()).isEqualTo(symbol);
            softly.assertThat(frameFill.side()).isEqualTo(side);
            softly.assertThat(frameFill.quantity()).isEqualByComparingTo("5");
        });
    }

    @RunDuringMarketHours
    @Test
    void liveFillsAreScopedToTheAuthenticatedUserAcrossWsAndRecentFillsApi() {
        Login alice = registerFreshUser("fillsa");
        Login bob = registerFreshUser("fillsb");
        FundsService.deposit(alice.token(), new BigDecimal("10000"));
        FundsService.deposit(bob.token(), new BigDecimal("10000"));

        Allure.step("Alice and Bob subscribe to their own fill feeds");
        WsSession aliceSession = NotificationsWsService.subscribeFills(alice.token());
        WsSession bobSession = NotificationsWsService.subscribeFills(bob.token());

        Allure.step("Alice places a BUY 1 AAPL and only Alice receives the live fill");
        ApiResponse aliceOrderResponse = OrdersService.placeMarketOrder(alice.token(), TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse aliceOrder = aliceOrderResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(aliceOrderResponse.statusCode()).isEqualTo(201);
            softly.assertThat(aliceOrder.id()).isNotBlank();
            softly.assertThat(aliceOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceOrder.side()).isEqualTo(Side.BUY);
        });
        Fill aliceFrame = aliceSession.nextFrameAs(Fill.class, SHORT_WS_FRAME_TIMEOUT);
        assertSoftly(softly -> {
            softly.assertThat(aliceFrame.orderId()).isEqualTo(aliceOrder.id());
            softly.assertThat(aliceFrame.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceFrame.side()).isEqualTo(Side.BUY);
            softly.assertThat(aliceFrame.quantity()).isEqualByComparingTo("1");
            softly.assertThat(bobSession.nextFrame(STABILITY_TIMEOUT)).isNull();
        });

        Allure.step("Bob places a BUY 1 MSFT and only Bob receives the live fill");
        ApiResponse bobOrderResponse = OrdersService.placeMarketOrder(bob.token(), TradingSymbols.MSFT, Side.BUY, BigDecimal.ONE);
        OrderResponse bobOrder = bobOrderResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(bobOrderResponse.statusCode()).isEqualTo(201);
            softly.assertThat(bobOrder.id()).isNotBlank();
            softly.assertThat(bobOrder.symbol()).isEqualTo(TradingSymbols.MSFT);
            softly.assertThat(bobOrder.side()).isEqualTo(Side.BUY);
        });
        Fill bobFrame = bobSession.nextFrameAs(Fill.class, SHORT_WS_FRAME_TIMEOUT);
        assertSoftly(softly -> {
            softly.assertThat(bobFrame.orderId()).isEqualTo(bobOrder.id());
            softly.assertThat(bobFrame.symbol()).isEqualTo(TradingSymbols.MSFT);
            softly.assertThat(bobFrame.side()).isEqualTo(Side.BUY);
            softly.assertThat(bobFrame.quantity()).isEqualByComparingTo("1");
            softly.assertThat(aliceSession.nextFrame(STABILITY_TIMEOUT)).isNull();
        });
        aliceSession.close();
        bobSession.close();

        Allure.step("The persisted recent-fills API remains owner-scoped for both users");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<Fill> aliceFills = PortfolioService.recentFills(alice.token()).asList(Fill.class);
            List<Fill> bobFills = PortfolioService.recentFills(bob.token()).asList(Fill.class);
            assertSoftly(softly -> {
                softly.assertThat(aliceFills).extracting(Fill::orderId).containsExactly(aliceOrder.id());
                softly.assertThat(bobFills).extracting(Fill::orderId).containsExactly(bobOrder.id());
            });
        });
    }

    // The one whole-chain price seam: a marketable LIMIT BUY (limit far above market) must fill at the
    // MARKET price across order→matcher→fill→portfolio, not at its stated limit. The directional
    // matcher logic itself is owned by the market-data-service OrderMatchingIT (no overlap).
    @RunDuringMarketHours
    @Test
    void limitOrderFillsAtMarketPriceNotAtTheLimitAcrossTheChain() {
        Allure.step("Place a LIMIT BUY 10 GOOG @ 100000 (immediately marketable)");
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, 10, new BigDecimal("100000"));
        ApiResponse response = OrdersService.placeOrder(this.token, limitBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("10");
            softly.assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(order.limitPrice()).isEqualByComparingTo("100000");
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });

        Allure.step("It fills at the persisted market fill price, not the 100000 limit");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Fill latestFill = PortfolioService.recentFills(token).asList(Fill.class).stream()
                    .filter(fill -> fill.orderId().equals(order.id()))
                    .findFirst()
                    .orElseThrow();
            Position googPosition = PortfolioService.positions(token).asList(Position.class).stream()
                    .filter(position -> position.symbol().equals(TradingSymbols.GOOG))
                    .findFirst()
                    .orElseThrow();
            assertSoftly(softly -> {
                softly.assertThat(latestFill.price()).isEqualByComparingTo(googPosition.avgPrice());
                softly.assertThat(latestFill.price()).isNotEqualByComparingTo("100000");
            });
        });
    }

    // A non-marketable LIMIT is a valid order (accepted, SUBMITTED) but never fills — the matcher rests
    // it, so it is saved with status PENDING. (Marketable typed fills are covered above; the matcher's
    // directional logic is owned by the market-data-service OrderMatchingIT.)
    @RunDuringMarketHours
    void pendingLimitOrderIsSubmittedAndResolvesToPending() {
        Allure.step("Place a non-marketable LIMIT BUY 1 GOOG (0.01)");
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, 1, new BigDecimal("0.01"));
        ApiResponse response = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("1");
            softly.assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("It is non-marketable, so the matcher rests it and it becomes PENDING");
        await().atMost(SHORT_AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse pendingOrder = findOrderById(orderId);
            assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        });
    }

    // A non-marketable STOP is a valid order (accepted, SUBMITTED) but never fills — the matcher rests
    // it, so it is saved with status PENDING. (Marketable typed fills are covered above; the matcher's
    // directional logic is owned by the market-data-service OrderMatchingIT.)
    @RunDuringMarketHours
    void pendingStopOrderIsSubmittedAndResolvesToPending() {
        Allure.step("Place a non-marketable STOP BUY 1 GOOG (1000000)");
        OrderRequest stopBuyOrderRequest = OrderFactory.stopBuy(TradingSymbols.GOOG, 1, new BigDecimal("1000000"));
        ApiResponse response = OrdersService.placeOrder(token, stopBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("1");
            softly.assertThat(order.type()).isEqualTo(OrderType.STOP);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("It is non-marketable, so the matcher rests it and it becomes PENDING");
        await().atMost(SHORT_AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse pendingOrder = findOrderById(orderId);
            assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        });
    }

    // Order lifecycle: a resting order can be cancelled and is then CANCELLED in history.
    // A marketable order races the matcher and can legitimately fill before the cancel lands,
    // which makes this scenario nondeterministic in a live stack.
    @RunDuringMarketHours
    @Test
    void aPlacedOrderCanBeCancelled() {
        Allure.step("Place a non-marketable LIMIT BUY order");
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, 1, new BigDecimal("0.01"));
        ApiResponse placedResponse = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(201);
            softly.assertThat(placedOrder.id()).isNotBlank();
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(placedOrder.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(placedOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = placedOrder.id();

        Allure.step("It resolves to PENDING before cancellation");
        await().atMost(SHORT_AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse pendingOrder = findOrderById(orderId);
            assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        });

        Allure.step("Cancel it");
        ApiResponse cancelled = OrdersService.cancelOrder(token, orderId);
        OrderResponse cancelledOrder = cancelled.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(cancelled.statusCode()).isEqualTo(200);
            softly.assertThat(cancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(cancelledOrder.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(cancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(cancelledOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(cancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(cancelledOrder.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(cancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        });

        Allure.step("It appears CANCELLED in history");
        await().atMost(SHORT_AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse cancelledOrderInHistory = findOrderById(orderId);
            assertThat(cancelledOrderInHistory.status()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Live price ticks stream to a STOMP subscriber (the ticker feed). Not market-hours gated — the
    // demo price feed runs continuously.
    @ParameterizedTest(name = "live price tick is pushed for {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void livePriceIsPushedOverWebSocketForASingleSubscribedSymbol(String symbol) {
        Allure.step("Subscribe to live prices over STOMP for " + symbol);
        WsSession session = NotificationsWsService.subscribePrices(token, List.of(symbol));

        Allure.step("A price tick is pushed to the subscriber for " + symbol);
        Price price = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
        session.close();
        assertSoftly(softly -> {
            softly.assertThat(price.symbol()).isEqualTo(symbol);
            softly.assertThat(price.price()).isNotNull();
        });
    }

    @Test
    void livePricesArePushedOverWebSocketForAllSubscribedSymbols() {
        Allure.step("Subscribe to live prices over STOMP for AAPL, MSFT, and GOOG");
        WsSession session = NotificationsWsService.subscribePrices(token, List.of(TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG));

        Allure.step("A price tick is pushed for each subscribed symbol");
        Price firstPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
        Price secondPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
        Price thirdPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
        session.close();

        assertSoftly(softly -> {
            softly.assertThat(firstPrice.price()).isNotNull();
            softly.assertThat(secondPrice.price()).isNotNull();
            softly.assertThat(thirdPrice.price()).isNotNull();
            softly.assertThat(List.of(firstPrice.symbol(), secondPrice.symbol(), thirdPrice.symbol()).stream()
                    .sorted(Comparator.naturalOrder())
                    .toList())
                    .isEqualTo(List.of(TradingSymbols.AAPL, TradingSymbols.GOOG, TradingSymbols.MSFT));
        });
    }

    // A brand-new user (registered this run) starts with an empty book, can place an order, and sees
    // it in their own portfolio + history — the full onboarding-to-trade journey.
    @RunDuringMarketHours
    @Test
    void aNewlyRegisteredUserCanTradeAndSeeTheirPortfolio() {
        Allure.step("Fund the account (a new user starts with zero cash)");
        FundsService.deposit(token, new BigDecimal("100000"));

        Allure.step("Place a BUY order");
        ApiResponse response = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, new BigDecimal("3"));
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(order.id()).isNotBlank();
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo("3");
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The order and the position appear under the new user");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            assertThat(findOrderById(orderId).id()).isEqualTo(orderId);
            Position aaplPosition = findPositionBySymbol(TradingSymbols.AAPL);
            assertThat(aaplPosition.quantity()).isEqualByComparingTo("3");
        });
    }

    // P&L flows through the live stack: after a fill and live price ticks, the position carries a
    // (non-null) market price and an unrealized P&L.
    @RunDuringMarketHours
    @Test
    void positionCarriesLiveMarketPriceAndPnl() {
        Allure.step("Buy AAPL");
        OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, new BigDecimal("2"));

        Allure.step("The AAPL position eventually has a live mark and P&L");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position aapl = PortfolioService.positions(token).asList(Position.class).stream()
                    .filter(position -> position.symbol().equals(TradingSymbols.AAPL))
                    .findFirst().orElseThrow();
            assertSoftly(softly -> {
                softly.assertThat(aapl.marketPrice()).isNotNull();
                softly.assertThat(aapl.unrealizedPnl()).isNotNull();
            });
        });
    }

    // Cancelling an already-cancelled order is idempotent (still 200 / CANCELLED).
    @RunDuringMarketHours
    @Test
    void cancellingAnAlreadyCancelledOrderIsIdempotent() {
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.AAPL, 1, new BigDecimal("0.01"));
        ApiResponse placedResponse = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(201);
            softly.assertThat(placedOrder.id()).isNotBlank();
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(placedOrder.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(placedOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = placedOrder.id();

        Allure.step("Wait until the non-marketable order is resting");
        await().atMost(SHORT_AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse pendingOrder = findOrderById(orderId);
            assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        });

        Allure.step("Cancel it twice");
        ApiResponse firstCancel = OrdersService.cancelOrder(token, orderId);
        OrderResponse firstCancelledOrder = firstCancel.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(firstCancel.statusCode()).isEqualTo(200);
            softly.assertThat(firstCancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(firstCancelledOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(firstCancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(firstCancelledOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(firstCancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(firstCancelledOrder.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(firstCancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        });
        ApiResponse second = OrdersService.cancelOrder(token, orderId);
        OrderResponse secondCancelledOrder = second.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(second.statusCode()).isEqualTo(200);
            softly.assertThat(secondCancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(secondCancelledOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(secondCancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(secondCancelledOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(secondCancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(secondCancelledOrder.limitPrice()).isEqualByComparingTo("0.01");
            softly.assertThat(secondCancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Order lifecycle: once the matcher has filled an order, cancellation must be rejected. This pins
    // the terminal FILLED state instead of racing a cancellation against an in-flight market order.
    @RunDuringMarketHours
    @Test
    void cancellingAnAlreadyFilledOrderIsRejectedWith409() {
        ApiResponse placedResponse = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, new BigDecimal("1"));
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(201);
            softly.assertThat(placedOrder.id()).isNotBlank();
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(placedOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = placedOrder.id();

        Allure.step("Wait until the matcher resolves the order to FILLED");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse filledOrder = findOrderById(orderId);
            assertThat(filledOrder.status()).isEqualTo(OrderStatus.FILLED);
        });

        Allure.step("Cancelling the already-filled order is rejected");
        ApiResponse cancelled = OrdersService.cancelOrder(token, orderId);
        ErrorResponse problem = cancelled.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(cancelled.statusCode()).isEqualTo(409);
            softly.assertThat(problem.error()).isEqualTo("Conflict");
            softly.assertThat(problem.status()).isEqualTo(409);
            softly.assertThat(problem.message()).isEqualTo("Filled orders cannot be cancelled: " + orderId);
            softly.assertThat(problem.path()).isEqualTo("/orders/" + orderId);
        });
    }

    // Authorization: one real user's trades are not visible to another. Alice and Bob log in for real
    // (separate tokens); the order owner is the token subject, and order history is owner-scoped.
    @RunDuringMarketHours
    @Test
    void aUserCannotSeeAnotherUsersTrades() {
        String alice = token;
        String bob = registerFreshUserToken("u");
        Allure.step("Alice places a BUY 1 AAPL");
        ApiResponse response = OrdersService.placeMarketOrder(alice, TradingSymbols.AAPL, Side.BUY, new BigDecimal("1"));
        OrderResponse aliceOrder = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(aliceOrder.id()).isNotBlank();
            softly.assertThat(aliceOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(aliceOrder.quantity()).isEqualByComparingTo("1");
            softly.assertThat(aliceOrder.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(aliceOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String aliceOrderId = aliceOrder.id();

        Allure.step("Alice sees her own order in history");
        List<OrderResponse> aliceOrders = OrderHistoryService.history(alice).asList(OrderResponse.class);
        assertThat(aliceOrders).extracting(OrderResponse::id).containsExactly(aliceOrderId);

        Allure.step("Bob does NOT see Alice's order");
        List<OrderResponse> bobOrders = OrderHistoryService.history(bob).asList(OrderResponse.class);
        assertThat(bobOrders).extracting(OrderResponse::id).doesNotContain(aliceOrderId);
    }

    // Authentication: an expired token is rejected by the deployed services (shared-secret JWT,
    // signature + expiry validated). Minted with a past expiry; not gated (a read-only auth check).
    @Test
    void expiredTokenIsRejectedWith401() {
        String expired = com.example.demo.common.security.Jwt.issue(
                username,
                "demo-secret-demo-secret-demo-secret-1234",
                Instant.parse("2026-07-02T15:00:10Z"),
                Instant.parse("2026-07-02T15:01:10Z"));
        Allure.step("An expired token is rejected with 401");
        ApiResponse response = PortfolioService.positions(expired);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("An error occurred while attempting to decode the Jwt: Jwt expired at 2026-07-02T15:01:10Z");
            softly.assertThat(problem.path()).isEqualTo("/portfolio");
        });
    }

    private Position findPositionBySymbol(String symbol) {
        return PortfolioService.positions(token).asList(Position.class).stream()
                .filter(position -> position.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + symbol));
    }

    private OrderResponse findOrderById(String orderId) {
        return OrderHistoryService.history(token).asList(OrderResponse.class).stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order for " + orderId));
    }

    private static Stream<Arguments> webSocketFillOrders() {
        return Stream.of(
                Arguments.of(Side.BUY, TradingSymbols.GOOG),
                Arguments.of(Side.SELL, TradingSymbols.AAPL));
    }

    private static Stream<Arguments> pendingTypedOrders() {
        return Stream.of(
                Arguments.of(OrderType.LIMIT, LIMIT_PRICE_FIELD, "0.01"),
                Arguments.of(OrderType.STOP, STOP_PRICE_FIELD, "1000000"));
    }
}
