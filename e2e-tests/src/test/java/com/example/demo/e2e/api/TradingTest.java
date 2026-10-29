package com.example.demo.e2e.api;

import com.example.demo.e2e.framework.config.Config;
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
import com.example.demo.e2e.framework.support.Lookup;
import com.example.demo.e2e.framework.support.OrderFactory;
import com.example.demo.e2e.framework.ws.NotificationsWsService;
import com.example.demo.e2e.framework.ws.WsSession;
import com.example.demo.e2e.harness.RunDuringMarketHours;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.example.demo.e2e.framework.support.ApiErrorMessages.INVALID_REQUEST_CONTENT;
import static com.example.demo.e2e.framework.support.ApiErrorMessages.JWT_DECODE_ERROR;
import static com.example.demo.e2e.framework.support.ApiPaths.ORDER;
import static com.example.demo.e2e.framework.support.ApiPaths.ORDERS;
import static com.example.demo.e2e.framework.support.ApiPaths.PORTFOLIO;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST_REASON;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CONFLICT;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CONFLICT_REASON;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.OK;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNAUTHORIZED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNAUTHORIZED_REASON;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

/**
 * API + WebSocket e2e suite (docs/DESIGN.md §7.2, §8). Black-box: drives an already-running stack
 * (locally {@code docker compose up}; in CI a deployed environment) through the facade service
 * objects only, narrating each step with {@code Allure.step(...)}. It never boots anything.
 *
 * <p>Every value is asserted exactly, even on a live environment: the order echoes the request and
 * its server-generated id is the one in the user's history; a fill references the placed order id;
 * live prices (the demo feed random-walks once a second) are compared with a second, independent read
 * of the same price taken back to back and retried until no tick lands in between. Skipped if the
 * stack is not up.
 */
class TradingTest extends BaseApiTest {

    private static final BigDecimal NEGATIVE_QUANTITY = new BigDecimal("-5");
    private static final BigDecimal WS_FILL_QUANTITY = new BigDecimal("5");
    private static final String FILLS_ALICE_PREFIX = "fillsa";
    private static final String FILLS_BOB_PREFIX = "fillsb";
    /** Enough cash for a brand-new user's BUY (a new user starts with zero cash). */
    private static final BigDecimal NEW_USER_DEPOSIT = new BigDecimal("100000");
    private static final BigDecimal NEW_USER_BUY_QUANTITY = new BigDecimal("3");
    private static final BigDecimal PNL_BUY_QUANTITY = new BigDecimal("2");
    /** The stack's shared HS256 secret: e2e config {@code jwt.secret} (env {@code JWT_SECRET}, as for the services). */
    private static final String JWT_SECRET = Config.get().get("jwt.secret");
    private static final Instant EXPIRED_TOKEN_ISSUED_AT = Instant.parse("2026-07-02T15:00:10Z");
    private static final String EXPIRED_TOKEN_EXPIRY = "2026-07-02T15:01:10Z";
    private static final String TOKEN_EXPIRED_REASON = "Jwt expired at " + EXPIRED_TOKEN_EXPIRY;
    private static final String FILLED_ORDER_NOT_CANCELLABLE = "Filled orders cannot be cancelled: ";

    @RunDuringMarketHours
    @ParameterizedTest(name = "BUY MARKET {0}")
    @ValueSource(strings = {TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG})
    void placingABuyMarketOrderIsAcknowledgedAndAppearsInThePortfolio(String symbol) {
        Allure.step("Place a marketable MARKET BUY order for 10 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, Side.BUY, ORDER_SIZE);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The " + symbol + " position appears in the portfolio");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertSoftly(softly -> {
                softly.assertThat(position.symbol()).isEqualTo(symbol);
                softly.assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
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
        OrderResponse seedOrder = OrdersService.placeMarketOrder(token, symbol, Side.BUY, SEEDED_POSITION).as(OrderResponse.class);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo(SEEDED_POSITION);
        });

        Allure.step("Place a marketable MARKET SELL order for 10 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, Side.SELL, ORDER_SIZE);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactlyInAnyOrder(seedOrder.id(), order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The " + symbol + " position remains represented in the portfolio");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
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
        Allure.step("Read the latest " + symbol + " price; a limit 10% above it is immediately marketable");
        BigDecimal limitPrice = OrderFactory.marketableBuyLimit(
                MarketDataService.latestPrice(token, symbol).as(Price.class).price());

        Allure.step("Place a marketable LIMIT BUY order for 10 " + symbol + " @ " + limitPrice);
        OrderRequest limitOrder = OrderFactory.limitBuy(symbol, ORDER_QUANTITY, limitPrice);
        ApiResponse response = OrdersService.placeOrder(token, limitOrder);
        OrderResponse orderResponse = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(orderResponse.id());
            softly.assertThat(orderResponse.symbol()).isEqualTo(symbol);
            softly.assertThat(orderResponse.side()).isEqualTo(Side.BUY);
            softly.assertThat(orderResponse.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(orderResponse.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(orderResponse.limitPrice()).isEqualByComparingTo(limitPrice);
            softly.assertThat(orderResponse.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = orderResponse.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
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
        OrderResponse seedOrder = OrdersService.placeMarketOrder(token, symbol, Side.BUY, SEEDED_POSITION).as(OrderResponse.class);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo(SEEDED_POSITION);
        });

        Allure.step("Place a marketable LIMIT SELL order for 10 " + symbol);
        OrderRequest limitSellOrderRequest = OrderFactory.limitSell(symbol, ORDER_QUANTITY, BELOW_MARKET_PRICE);
        ApiResponse response = OrdersService.placeOrder(token, limitSellOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactlyInAnyOrder(seedOrder.id(), order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(order.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
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
        OrderRequest stopBuyOrderRequest = OrderFactory.stopBuy(symbol, ORDER_QUANTITY, BELOW_MARKET_PRICE);
        ApiResponse response = OrdersService.placeOrder(token, stopBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.STOP);
            softly.assertThat(order.stopPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
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
        OrderResponse seedOrder = OrdersService.placeMarketOrder(token, symbol, Side.BUY, SEEDED_POSITION).as(OrderResponse.class);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo(SEEDED_POSITION);
        });

        Allure.step("Read the latest " + symbol + " price; a SELL stop 10% above it is already breached");
        BigDecimal stopPrice = OrderFactory.marketableSellStop(
                MarketDataService.latestPrice(token, symbol).as(Price.class).price());

        Allure.step("Place a marketable STOP SELL order for 10 " + symbol + " @ " + stopPrice);
        OrderRequest stopSellOrderRequest = OrderFactory.stopSell(symbol, ORDER_QUANTITY, stopPrice);
        ApiResponse response = OrdersService.placeOrder(token, stopSellOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactlyInAnyOrder(seedOrder.id(), order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(Side.SELL);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.STOP);
            softly.assertThat(order.stopPrice()).isEqualByComparingTo(stopPrice);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position position = position(symbol);
            assertThat(position.quantity()).isEqualByComparingTo(ORDER_SIZE);
        });
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            OrderResponse resolvedOrder = findOrderById(orderId);
            assertThat(resolvedOrder.status()).isEqualTo(OrderStatus.FILLED);
        });
    }

    @Test
    void latestPriceIsServed() {
        Allure.step("Subscribe to the live AAPL ticker, the independent source of the latest price");
        WsSession session = NotificationsWsService.subscribePrices(token, List.of(TradingSymbols.AAPL));

        // The demo feed random-walks once a second: the latest pushed tick and the served price are
        // read back to back and retried until no new tick lands between the two reads.
        Allure.step("market-data serves exactly the latest pushed AAPL price");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Price pushedPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
            ApiResponse response = MarketDataService.latestPrice(token, TradingSymbols.AAPL);
            Price price = response.as(Price.class);
            assertSoftly(softly -> {
                softly.assertThat(response.statusCode()).isEqualTo(OK);
                softly.assertThat(price.symbol()).isEqualTo(TradingSymbols.AAPL);
                softly.assertThat(price.price()).isEqualByComparingTo(pushedPrice.price());
            });
        });
        session.close();
    }

    @Test
    void invalidOrderIsRejectedWith400() {
        Allure.step("Submitting a blank symbol returns 400 (no client-side validation)");
        ApiResponse response = OrdersService.placeMarketOrder(token, "", Side.BUY, NEGATIVE_QUANTITY);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.error()).isEqualTo(BAD_REQUEST_REASON);
            softly.assertThat(problem.status()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.message()).isEqualTo(INVALID_REQUEST_CONTENT);
            softly.assertThat(problem.path()).isEqualTo(ORDERS);
        });
    }

    // Both sides exercised end-to-end: this proves the order→match→fill→WS-push chain carries a SELL
    // as well as a BUY. The SELL position arithmetic itself is owned by the portfolio-service FillsListenerIT (no overlap).
    @RunDuringMarketHours
    @ParameterizedTest(name = "{0} fill for {1} is pushed over WebSocket")
    @MethodSource("webSocketFillOrders")
    void fillIsPushedOverWebSocketForThePlacedOrder(Side side, String symbol) {
        Allure.step("Seed a position in " + symbol + " so a SELL passes the short-sell guard");
        OrderResponse seedOrder = OrdersService.placeMarketOrder(token, symbol, Side.BUY, ORDER_SIZE).as(OrderResponse.class);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position seededPosition = position(symbol);
            assertThat(seededPosition.quantity()).isEqualByComparingTo(ORDER_SIZE);
        });

        Allure.step("Subscribe to fills over WebSocket (STOMP)");
        WsSession session = NotificationsWsService.subscribeFills(token);

        Allure.step("Place a " + side + " order for 5 " + symbol);
        ApiResponse response = OrdersService.placeMarketOrder(token, symbol, side, WS_FILL_QUANTITY);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactlyInAnyOrder(seedOrder.id(), order.id());
            softly.assertThat(order.symbol()).isEqualTo(symbol);
            softly.assertThat(order.side()).isEqualTo(side);
            softly.assertThat(order.quantity()).isEqualByComparingTo(WS_FILL_QUANTITY);
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
            softly.assertThat(frameFill.quantity()).isEqualByComparingTo(WS_FILL_QUANTITY);
        });
    }

    @RunDuringMarketHours
    @Test
    void liveFillsAreScopedToTheAuthenticatedUserAcrossWsAndRecentFillsApi() {
        Login alice = registerFreshUser(FILLS_ALICE_PREFIX);
        Login bob = registerFreshUser(FILLS_BOB_PREFIX);
        FundsService.deposit(alice.token(), DEFAULT_STARTING_CASH);
        FundsService.deposit(bob.token(), DEFAULT_STARTING_CASH);

        Allure.step("Alice and Bob subscribe to their own fill feeds");
        WsSession aliceSession = NotificationsWsService.subscribeFills(alice.token());
        WsSession bobSession = NotificationsWsService.subscribeFills(bob.token());

        Allure.step("Alice places a BUY 1 AAPL and only Alice receives the live fill");
        ApiResponse aliceOrderResponse = OrdersService.placeMarketOrder(alice.token(), TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse aliceOrder = aliceOrderResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(aliceOrderResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(alice.token())).extracting(OrderResponse::id).containsExactly(aliceOrder.id());
            softly.assertThat(aliceOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceOrder.side()).isEqualTo(Side.BUY);
        });
        Fill aliceFrame = aliceSession.nextFrameAs(Fill.class, SHORT_WS_FRAME_TIMEOUT);
        assertSoftly(softly -> {
            softly.assertThat(aliceFrame.orderId()).isEqualTo(aliceOrder.id());
            softly.assertThat(aliceFrame.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceFrame.side()).isEqualTo(Side.BUY);
            softly.assertThat(aliceFrame.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(bobSession.nextFrame(STABILITY_TIMEOUT)).isNull();
        });

        Allure.step("Bob places a BUY 1 MSFT and only Bob receives the live fill");
        ApiResponse bobOrderResponse = OrdersService.placeMarketOrder(bob.token(), TradingSymbols.MSFT, Side.BUY, BigDecimal.ONE);
        OrderResponse bobOrder = bobOrderResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(bobOrderResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(bob.token())).extracting(OrderResponse::id).containsExactly(bobOrder.id());
            softly.assertThat(bobOrder.symbol()).isEqualTo(TradingSymbols.MSFT);
            softly.assertThat(bobOrder.side()).isEqualTo(Side.BUY);
        });
        Fill bobFrame = bobSession.nextFrameAs(Fill.class, SHORT_WS_FRAME_TIMEOUT);
        assertSoftly(softly -> {
            softly.assertThat(bobFrame.orderId()).isEqualTo(bobOrder.id());
            softly.assertThat(bobFrame.symbol()).isEqualTo(TradingSymbols.MSFT);
            softly.assertThat(bobFrame.side()).isEqualTo(Side.BUY);
            softly.assertThat(bobFrame.quantity()).isEqualByComparingTo(BigDecimal.ONE);
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
        Allure.step("Read the latest GOOG price; a limit 10% above it is immediately marketable");
        BigDecimal limitPrice = OrderFactory.marketableBuyLimit(
                MarketDataService.latestPrice(token, TradingSymbols.GOOG).as(Price.class).price());

        Allure.step("Place a LIMIT BUY 10 GOOG @ " + limitPrice + " (immediately marketable)");
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, ORDER_QUANTITY, limitPrice);
        ApiResponse response = OrdersService.placeOrder(this.token, limitBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(ORDER_SIZE);
            softly.assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(order.limitPrice()).isEqualByComparingTo(limitPrice);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });

        Allure.step("It fills at the persisted market fill price, not the " + limitPrice + " limit");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Fill latestFill = Lookup.fill(PortfolioService.recentFills(token).asList(Fill.class), order.id());
            Position googPosition = position(TradingSymbols.GOOG);
            assertSoftly(softly -> {
                softly.assertThat(latestFill.price()).isEqualByComparingTo(googPosition.avgPrice());
                softly.assertThat(latestFill.price()).isNotEqualByComparingTo(limitPrice);
            });
        });
    }

    // A non-marketable LIMIT is a valid order (accepted, SUBMITTED) but never fills — the matcher rests
    // it, so it is saved with status PENDING. (Marketable typed fills are covered above; the matcher's
    // directional logic is owned by the market-data-service OrderMatchingIT.)
    @Test
    @RunDuringMarketHours
    void pendingLimitOrderIsSubmittedAndResolvesToPending() {
        Allure.step("Place a non-marketable LIMIT BUY 1 GOOG (0.01)");
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, 1, BELOW_MARKET_PRICE);
        ApiResponse response = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(BigDecimal.ONE);
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
    @Test
    void pendingStopOrderIsSubmittedAndResolvesToPending() {
        // 10x the demo prices: never reached by the feed (non-marketable), notional within risk limits.
        Allure.step("Place a non-marketable STOP BUY 1 GOOG (1000)");
        OrderRequest stopBuyOrderRequest = OrderFactory.stopBuy(TradingSymbols.GOOG, 1, UNREACHABLE_STOP_PRICE);
        ApiResponse response = OrdersService.placeOrder(token, stopBuyOrderRequest);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(BigDecimal.ONE);
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
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.GOOG, 1, BELOW_MARKET_PRICE);
        ApiResponse placedResponse = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(placedOrder.id());
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(placedOrder.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
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
            softly.assertThat(cancelled.statusCode()).isEqualTo(OK);
            softly.assertThat(cancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(cancelledOrder.symbol()).isEqualTo(TradingSymbols.GOOG);
            softly.assertThat(cancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(cancelledOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(cancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(cancelledOrder.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
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

        // The pushed tick and the price market-data serves are read back to back and retried until
        // no new tick (one a second) lands between the two reads.
        Allure.step("A price tick is pushed for " + symbol + " carrying exactly the price market-data serves");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Price price = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
            Price servedPrice = MarketDataService.latestPrice(token, symbol).as(Price.class);
            assertSoftly(softly -> {
                softly.assertThat(price.symbol()).isEqualTo(symbol);
                softly.assertThat(price.price()).isEqualByComparingTo(servedPrice.price());
            });
        });
        session.close();
    }

    @Test
    void livePricesArePushedOverWebSocketForAllSubscribedSymbols() {
        Allure.step("Subscribe to live prices over STOMP for AAPL, MSFT, and GOOG");
        WsSession session = NotificationsWsService.subscribePrices(token, List.of(TradingSymbols.AAPL, TradingSymbols.MSFT, TradingSymbols.GOOG));

        // Each pushed tick is compared with the price market-data serves for its symbol, read right
        // after; retried until no new tick round (one a second) lands between the reads.
        Allure.step("A price tick is pushed for each subscribed symbol, carrying exactly the served price");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Price firstPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
            Price secondPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
            Price thirdPrice = session.nextFrameAs(Price.class, WS_FRAME_TIMEOUT);
            Price firstServed = MarketDataService.latestPrice(token, firstPrice.symbol()).as(Price.class);
            Price secondServed = MarketDataService.latestPrice(token, secondPrice.symbol()).as(Price.class);
            Price thirdServed = MarketDataService.latestPrice(token, thirdPrice.symbol()).as(Price.class);
            assertSoftly(softly -> {
                softly.assertThat(firstPrice.price()).isEqualByComparingTo(firstServed.price());
                softly.assertThat(secondPrice.price()).isEqualByComparingTo(secondServed.price());
                softly.assertThat(thirdPrice.price()).isEqualByComparingTo(thirdServed.price());
                softly.assertThat(List.of(firstPrice.symbol(), secondPrice.symbol(), thirdPrice.symbol()).stream()
                        .sorted(Comparator.naturalOrder())
                        .toList())
                        .isEqualTo(List.of(TradingSymbols.AAPL, TradingSymbols.GOOG, TradingSymbols.MSFT));
            });
        });
        session.close();
    }

    // A brand-new user (registered this run) starts with an empty book, can place an order, and sees
    // it in their own portfolio + history — the full onboarding-to-trade journey.
    @RunDuringMarketHours
    @Test
    void aNewlyRegisteredUserCanTradeAndSeeTheirPortfolio() {
        Allure.step("Fund the account (a new user starts with zero cash)");
        FundsService.deposit(token, NEW_USER_DEPOSIT);

        Allure.step("Place a BUY order");
        ApiResponse response = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, NEW_USER_BUY_QUANTITY);
        OrderResponse order = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(order.id());
            softly.assertThat(order.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(order.side()).isEqualTo(Side.BUY);
            softly.assertThat(order.quantity()).isEqualByComparingTo(NEW_USER_BUY_QUANTITY);
            softly.assertThat(order.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String orderId = order.id();

        Allure.step("The order and the position appear under the new user");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            assertThat(findOrderById(orderId).id()).isEqualTo(orderId);
            Position aaplPosition = position(TradingSymbols.AAPL);
            assertThat(aaplPosition.quantity()).isEqualByComparingTo(NEW_USER_BUY_QUANTITY);
        });
    }

    // P&L flows through the live stack: after a fill and live price ticks, the position is marked at
    // exactly the price market-data serves, and its unrealized P&L is (mark - average cost) × quantity.
    @RunDuringMarketHours
    @Test
    void positionCarriesLiveMarketPriceAndPnl() {
        Allure.step("Buy AAPL");
        OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, PNL_BUY_QUANTITY);

        // The mark follows the once-a-second demo feed through Kafka, so position and served price are
        // read back to back and retried until both reflect the same tick.
        Allure.step("The AAPL position is marked at exactly the live price market-data serves");
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Position aapl = position(TradingSymbols.AAPL);
            Price servedPrice = MarketDataService.latestPrice(token, TradingSymbols.AAPL).as(Price.class);
            assertSoftly(softly -> {
                softly.assertThat(aapl.quantity()).isEqualByComparingTo(PNL_BUY_QUANTITY);
                softly.assertThat(aapl.marketPrice()).isEqualByComparingTo(servedPrice.price());
            });
        });

        Allure.step("Its unrealized P&L is exactly (mark - average cost) × quantity");
        Position markedPosition = position(TradingSymbols.AAPL);
        BigDecimal expectedPnl = markedPosition.marketPrice().subtract(markedPosition.avgPrice())
                .multiply(markedPosition.quantity());
        assertThat(markedPosition.unrealizedPnl()).isEqualByComparingTo(expectedPnl);
    }

    // Cancelling an already-cancelled order is idempotent (still 200 / CANCELLED).
    @RunDuringMarketHours
    @Test
    void cancellingAnAlreadyCancelledOrderIsIdempotent() {
        OrderRequest limitBuyOrderRequest = OrderFactory.limitBuy(TradingSymbols.AAPL, 1, BELOW_MARKET_PRICE);
        ApiResponse placedResponse = OrdersService.placeOrder(token, limitBuyOrderRequest);
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(placedOrder.id());
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(placedOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(placedOrder.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
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
            softly.assertThat(firstCancel.statusCode()).isEqualTo(OK);
            softly.assertThat(firstCancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(firstCancelledOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(firstCancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(firstCancelledOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(firstCancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(firstCancelledOrder.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
            softly.assertThat(firstCancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        });
        ApiResponse second = OrdersService.cancelOrder(token, orderId);
        OrderResponse secondCancelledOrder = second.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(second.statusCode()).isEqualTo(OK);
            softly.assertThat(secondCancelledOrder.id()).isEqualTo(orderId);
            softly.assertThat(secondCancelledOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(secondCancelledOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(secondCancelledOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(secondCancelledOrder.type()).isEqualTo(OrderType.LIMIT);
            softly.assertThat(secondCancelledOrder.limitPrice()).isEqualByComparingTo(BELOW_MARKET_PRICE);
            softly.assertThat(secondCancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Order lifecycle: once the matcher has filled an order, cancellation must be rejected. This pins
    // the terminal FILLED state instead of racing a cancellation against an in-flight market order.
    @RunDuringMarketHours
    @Test
    void cancellingAnAlreadyFilledOrderIsRejectedWith409() {
        ApiResponse placedResponse = OrdersService.placeMarketOrder(token, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse placedOrder = placedResponse.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(placedResponse.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(token)).extracting(OrderResponse::id).containsExactly(placedOrder.id());
            softly.assertThat(placedOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(placedOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(placedOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
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
            softly.assertThat(cancelled.statusCode()).isEqualTo(CONFLICT);
            softly.assertThat(problem.error()).isEqualTo(CONFLICT_REASON);
            softly.assertThat(problem.status()).isEqualTo(CONFLICT);
            softly.assertThat(problem.message()).isEqualTo(FILLED_ORDER_NOT_CANCELLABLE + orderId);
            softly.assertThat(problem.path()).isEqualTo(ORDER + orderId);
        });
    }

    // Authorization: one real user's trades are not visible to another. Alice and Bob log in for real
    // (separate tokens); the order owner is the token subject, and order history is owner-scoped.
    @RunDuringMarketHours
    @Test
    void aUserCannotSeeAnotherUsersTrades() {
        String alice = token;
        String bob = registerFreshUserToken(USERNAME_PREFIX);
        Allure.step("Alice places a BUY 1 AAPL");
        ApiResponse response = OrdersService.placeMarketOrder(alice, TradingSymbols.AAPL, Side.BUY, BigDecimal.ONE);
        OrderResponse aliceOrder = response.as(OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(orderHistory(alice)).extracting(OrderResponse::id).containsExactly(aliceOrder.id());
            softly.assertThat(aliceOrder.symbol()).isEqualTo(TradingSymbols.AAPL);
            softly.assertThat(aliceOrder.side()).isEqualTo(Side.BUY);
            softly.assertThat(aliceOrder.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(aliceOrder.type()).isEqualTo(OrderType.MARKET);
            softly.assertThat(aliceOrder.status()).isEqualTo(OrderStatus.SUBMITTED);
        });
        String aliceOrderId = aliceOrder.id();

        Allure.step("Alice sees her own order in history");
        List<OrderResponse> aliceOrders = OrderHistoryService.history(alice).asList(OrderResponse.class);
        assertThat(aliceOrders).extracting(OrderResponse::id).containsExactly(aliceOrderId);

        Allure.step("Bob does NOT see Alice's order");
        List<OrderResponse> bobOrders = OrderHistoryService.history(bob).asList(OrderResponse.class);
        assertThat(bobOrders).isEmpty();
    }

    // Authentication: an expired token is rejected by the deployed services (shared-secret JWT,
    // signature + expiry validated). Minted with a past expiry; not gated (a read-only auth check).
    @Test
    void expiredTokenIsRejectedWith401() {
        String expired = com.example.demo.common.security.Jwt.issue(
                username,
                JWT_SECRET,
                EXPIRED_TOKEN_ISSUED_AT,
                Instant.parse(EXPIRED_TOKEN_EXPIRY));
        Allure.step("An expired token is rejected with 401");
        ApiResponse response = PortfolioService.positions(expired);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.error()).isEqualTo(UNAUTHORIZED_REASON);
            softly.assertThat(problem.status()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.message()).isEqualTo(JWT_DECODE_ERROR + TOKEN_EXPIRED_REASON);
            softly.assertThat(problem.path()).isEqualTo(PORTFOLIO);
        });
    }

    private OrderResponse findOrderById(String orderId) {
        return Lookup.order(orderHistory(token), orderId);
    }

    private static List<OrderResponse> orderHistory(String userToken) {
        return OrderHistoryService.history(userToken).asList(OrderResponse.class);
    }

    private static Stream<Arguments> webSocketFillOrders() {
        return Stream.of(
                Arguments.of(Side.BUY, TradingSymbols.GOOG),
                Arguments.of(Side.SELL, TradingSymbols.AAPL));
    }

}
