package com.example.demo.order.it;

import static com.example.demo.testing.support.auth.TestJwt.bearer;
import static com.example.demo.testing.support.auth.TestJwt.expired;
import static com.example.demo.testing.support.auth.TestJwt.forged;
import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.ALICE;
import static com.example.demo.testing.support.fixtures.TestUsers.BOB;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.domain.OrderType;
import com.example.demo.order.domain.Side;
import com.example.demo.order.events.OrderEventPublisher;
import com.example.demo.order.it.harness.ApiError;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderRequest;
import com.example.demo.order.it.harness.Orders;
import com.example.demo.order.it.harness.OrderSubmittedMessage;
import com.example.demo.order.exchange.ExchangeOrderRequest;
import com.example.demo.order.it.harness.stubs.ExchangeStubs;
import com.example.demo.order.it.harness.stubs.MarketDataStubs;
import com.example.demo.order.it.harness.stubs.PortfolioStubs;
import com.example.demo.order.it.harness.stubs.RiskCheckRequest;
import com.example.demo.order.it.harness.stubs.RiskStubs;
import com.example.demo.order.web.OrderResponse;
import com.example.demo.testing.support.fixtures.TradingInstants;

/**
 * {@code POST /orders}: validation, authentication, ownership, the working-hours gate, the pre-trade
 * short-sell (SELL) and funds (BUY) guards, routing to the exchange, persistence and the
 * {@code orders.submitted} event.
 */
class PlaceOrderIT extends OrderITBase {

    private static final String SYMBOL = "AAPL";
    private static final String ONE_SHARE = "1";

    // happy path
    private static final String ROUTED_SYMBOL = "HDLNE";
    private static final String ROUTED_QUANTITY = "10";
    private static final String BOTH_SIDES_QUANTITY = "3";
    /** A fractional quantity whose scale must be echoed back unchanged. */
    private static final String FRACTIONAL_QUANTITY = "2.5";

    // error bodies
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request content.";
    private static final String MARKET_CLOSED_MESSAGE = "Market is closed";
    private static final String INSUFFICIENT_HOLDINGS_MESSAGE = "Insufficient holdings to sell %s %s (held %s)";

    // authentication
    private static final String EXPIRED = "expired";
    private static final String FORGED = "forged";
    private static final String MALFORMED = "malformed";
    private static final String EXPIRED_AUTHORIZATION = bearer(expired(TESTER));
    private static final String FORGED_AUTHORIZATION = bearer(forged(TESTER));
    private static final String MALFORMED_AUTHORIZATION = bearer("not-a-jwt");

    // short-sell guard
    private static final String SELL_QUANTITY = "10";

    // funds guard: cost = 1 × 100 = 100
    private static final String FUNDED_SYMBOL = "FUND";
    private static final String FUNDED_PRICE = "100";
    private static final String INSUFFICIENT_CASH = "50";
    private static final String SUFFICIENT_CASH = "1000";
    private static final String NO_CASH = "0";
    private static final String INSUFFICIENT_FUNDS_MESSAGE = "Insufficient funds to buy 1 FUND (cost 100, available 50)";

    // no market price
    private static final String UNPRICED_SYMBOL = "NOPX";
    private static final String UNPRICED_LIMIT = "5";
    private static final String CANNOT_BE_VALUED_MESSAGE = "Order cannot be valued: no market price for NOPX";

    // risk check
    private static final String RISK_SYMBOL = "RISKV";
    private static final String RISK_QUANTITY = "2";

    // exchange outcomes
    private static final String DECLINED_SYMBOL = "REJSYM";
    private static final String EXCHANGE_FAILURE_OWNER = "exchange-5xx";
    private static final String EXCHANGE_FAILURE_SYMBOL = "EXFAIL";
    private static final String EXCHANGE_FAILURE_MESSAGE = "Exchange returned an invalid response";

    private static final OrderRequest ONE_SHARE_BUY = Orders.market(SYMBOL, Side.BUY.name(), ONE_SHARE);

    // ---- happy path: persisted, routed, published ----

    @Test
    void placeOrderPersistsRoutesAndPublishes() {
        OrderRequest order = Orders.market(ROUTED_SYMBOL, Side.BUY.name(), ROUTED_QUANTITY);
        String body = api(TESTER_TOKEN)
                .body(order)
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.CREATED.value())
                .extract().asString();

        OrderResponse response = Json.read(body, OrderResponse.class);
        OrderSubmittedMessage event = Json.read(
                KAFKA_PROBE.awaitValue(OrderEventPublisher.TOPIC, response.id(), EVENT_TIMEOUT),
                OrderSubmittedMessage.class);
        assertSoftly(softly -> {
            softly.assertThat(response.symbol()).isEqualTo(ROUTED_SYMBOL);
            softly.assertThat(response.side()).isEqualTo(Side.BUY.name());
            softly.assertThat(response.quantity()).isEqualByComparingTo(ROUTED_QUANTITY);
            softly.assertThat(response.type()).isEqualTo(OrderType.MARKET.name());
            softly.assertThat(response.owner()).isEqualTo(TESTER);
            softly.assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
            // The persisted row, not just the API response.
            softly.assertThat(statusOf(response.id())).isEqualTo(OrderStatus.SUBMITTED);
            softly.assertThat(event.id()).isEqualTo(response.id());
            softly.assertThat(event.symbol()).isEqualTo(ROUTED_SYMBOL);
            softly.assertThat(event.side()).isEqualTo(Side.BUY.name());
            softly.assertThat(event.quantity()).isEqualByComparingTo(ROUTED_QUANTITY);
            softly.assertThat(event.type()).isEqualTo(OrderType.MARKET.name());
            softly.assertThat(event.status()).isEqualTo(OrderStatus.SUBMITTED.name());
            softly.assertThat(event.owner()).isEqualTo(TESTER);
        });
        // The exchange receives exactly the order as placed (same fields, same values).
        EXCHANGE.verify(postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS))
                .withRequestBody(equalToJson(Json.write(
                        new ExchangeOrderRequest(ROUTED_SYMBOL, Side.BUY.name(), new BigDecimal(ROUTED_QUANTITY))))));
    }

    @ParameterizedTest(name = "side {0} is accepted")
    @ValueSource(strings = {"BUY", "SELL"})
    void bothSidesAreAcceptedAndSubmitted(String side) {
        OrderResponse response = place(TESTER_TOKEN, Orders.market(SYMBOL, side, BOTH_SIDES_QUANTITY));

        assertSoftly(softly -> {
            softly.assertThat(response.side()).isEqualTo(side);
            softly.assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
        });
    }

    // BVA on-point of @Positive (0.0001) plus an absurd size — documents there is no upper cap.
    @ParameterizedTest(name = "quantity {0} is accepted")
    @ValueSource(strings = {"0.0001", "1", "999999999"})
    void acceptsValidQuantityBoundaries(String quantity) {
        OrderResponse response = place(TESTER_TOKEN, Orders.market(SYMBOL, Side.BUY.name(), quantity));

        assertSoftly(softly -> {
            softly.assertThat(response.quantity()).isEqualByComparingTo(quantity);
            softly.assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
        });
    }

    @ParameterizedTest(name = "type {0} is accepted")
    @CsvSource(delimiter = '|', value = {
        "MARKET |        |",
        "LIMIT  | 195.50 |",
        "STOP   |        | 205.00"
    })
    void acceptsEachOrderType(String type, BigDecimal limitPrice, BigDecimal stopPrice) {
        OrderRequest order = new OrderRequest(SYMBOL, Side.BUY.name(), new BigDecimal(ONE_SHARE), type,
                limitPrice, stopPrice);

        OrderResponse response = place(TESTER_TOKEN, order);

        assertSoftly(softly -> {
            softly.assertThat(response.type()).isEqualTo(type);
            softly.assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
        });
    }

    // ---- request validation ----

    // EP/BVA invalid partitions: qty 0 (off-point of @Positive), negative, missing; blank/whitespace
    // symbol (@NotBlank); null and unparseable side; LIMIT/STOP missing or non-positive required price.
    // An empty cell is a null field; '' is the empty string.
    @ParameterizedTest(name = "symbol [{0}] side {1} quantity {2} type {3} limit {4} stop {5} -> 400")
    @CsvSource(delimiter = '|', value = {
        "AAPL  | BUY  | 0  |       |    |",
        "AAPL  | BUY  | -5 |       |    |",
        "AAPL  | BUY  |    |       |    |",
        "''    | BUY  | 10 |       |    |",
        "'   ' | BUY  | 10 |       |    |",
        "AAPL  |      | 10 |       |    |",
        "AAPL  | HODL | 10 |       |    |",
        "AAPL  | BUY  | 1  | LIMIT |    |",
        "AAPL  | BUY  | 1  | STOP  |    |",
        "AAPL  | BUY  | 1  | LIMIT | 0  |",
        "AAPL  | BUY  | 1  | LIMIT | -5 |",
        "AAPL  | BUY  | 1  | STOP  |    | 0",
        "AAPL  | BUY  | 1  | STOP  |    | -5"
    })
    void rejectsInvalidOrderWith400(String symbol, String side, BigDecimal quantity, String type,
                                    BigDecimal limitPrice, BigDecimal stopPrice) {
        OrderRequest order = new OrderRequest(symbol, side, quantity, type, limitPrice, stopPrice);

        String response = api(TESTER_TOKEN).body(order).post(ORDERS_PATH)
                .then().statusCode(HttpStatus.BAD_REQUEST.value())
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            softly.assertThat(error.message()).isEqualTo(INVALID_REQUEST_MESSAGE);
            softly.assertThat(error.path()).isEqualTo(ORDERS_PATH);
        });
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // ---- authentication & ownership ----

    @Test
    void rejectsRequestWithoutTokenWith401() {
        api().body(Orders.market(SYMBOL, Side.BUY.name(), ROUTED_QUANTITY)).post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());

        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // An expired token, a forged one (signed with the wrong secret), and a malformed one are all 401.
    @ParameterizedTest(name = "{0} token -> 401")
    @MethodSource("badTokens")
    void rejectsInvalidTokensWith401(String label, String authorization) {
        api().header(HttpHeaders.AUTHORIZATION, authorization)
                .body(ONE_SHARE_BUY)
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());

        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    static Stream<Arguments> badTokens() {
        return Stream.of(
                Arguments.of(EXPIRED, EXPIRED_AUTHORIZATION),
                Arguments.of(FORGED, FORGED_AUTHORIZATION),
                Arguments.of(MALFORMED, MALFORMED_AUTHORIZATION));
    }

    // Ownership comes from the token's subject, never the body — you cannot place an order as someone else.
    @Test
    void orderIsOwnedByTheAuthenticatedSubject() {
        OrderResponse response = place(ALICE_TOKEN, Orders.claimingOwner(SYMBOL, Side.BUY.name(), ONE_SHARE, BOB));

        assertSoftly(softly -> {
            softly.assertThat(response.owner()).isEqualTo(ALICE);
            softly.assertThat(repository.findById(UUID.fromString(response.id())).orElseThrow().getOwner())
                    .isEqualTo(ALICE);
        });
    }

    // ---- working-hours gate (injected clock) ----

    @Test
    void acceptsOrdersDuringWorkingHours() {
        clock.set(TradingInstants.DURING_HOURS); // Wed 14:00 ET — open

        OrderResponse response = place(TESTER_TOKEN, ONE_SHARE_BUY);

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
    }

    @ParameterizedTest(name = "market closed at {0}")
    @CsvSource({
        "2024-06-12T07:00:00Z",  // Wed 03:00 ET — before open
        "2024-06-15T18:00:00Z"   // Sat 14:00 ET — weekend, market closed
    })
    void rejectsOrdersOutsideWorkingHours(String instant) {
        clock.set(Instant.parse(instant));

        String response = api(TESTER_TOKEN).body(ONE_SHARE_BUY).post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            softly.assertThat(error.message()).isEqualTo(MARKET_CLOSED_MESSAGE);
        });
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // ---- short-sell guard (SELL) — portfolio-service holdings lookup ----

    @ParameterizedTest(name = "held {0}, sell {1} -> 422")
    @CsvSource({
        "5, 10",  // beyond holdings
        "0, 1"    // no holdings at all
    })
    void rejectsSellBeyondHoldingsWith422(String held, String quantity) {
        PORTFOLIO.stubFor(PortfolioStubs.holds(SYMBOL, held));

        String response = api(TESTER_TOKEN).body(Orders.market(SYMBOL, Side.SELL.name(), quantity))
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .extract().asString();

        assertThat(Json.read(response, ApiError.class).message())
                .isEqualTo(INSUFFICIENT_HOLDINGS_MESSAGE.formatted(quantity, SYMBOL, held));
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // BVA: well within holdings, and on-point (selling exactly the held quantity — the guard rejects only qty > held).
    @ParameterizedTest(name = "held {0}, sell 10 -> 201")
    @ValueSource(strings = {"50", "10"})
    void allowsSellWithinHoldings(String held) {
        PORTFOLIO.stubFor(PortfolioStubs.holds(SYMBOL, held));

        OrderResponse response = place(TESTER_TOKEN, Orders.market(SYMBOL, Side.SELL.name(), SELL_QUANTITY));

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
    }

    // ---- funds guard (BUY) — market-data price × quantity vs portfolio cash ----

    @Test
    void aBuyIsRejectedWhenFundsAreInsufficient() {
        MARKET_DATA.stubFor(MarketDataStubs.hasPrice(FUNDED_SYMBOL, FUNDED_PRICE));
        PORTFOLIO.stubFor(PortfolioStubs.hasCash(INSUFFICIENT_CASH));

        // cost = 1 × 100 = 100 > 50 available → rejected before routing
        String response = api(TESTER_TOKEN).body(Orders.market(FUNDED_SYMBOL, Side.BUY.name(), ONE_SHARE))
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .extract().asString();

        assertThat(Json.read(response, ApiError.class).message()).isEqualTo(INSUFFICIENT_FUNDS_MESSAGE);
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    @Test
    void aBuyIsAcceptedWhenFundsAreSufficient() {
        MARKET_DATA.stubFor(MarketDataStubs.hasPrice(FUNDED_SYMBOL, FUNDED_PRICE));
        PORTFOLIO.stubFor(PortfolioStubs.hasCash(SUFFICIENT_CASH));

        OrderResponse response = place(TESTER_TOKEN, Orders.market(FUNDED_SYMBOL, Side.BUY.name(), ONE_SHARE));

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
    }

    // No cached market price → the funds guard can't value the BUY and is skipped (accepted even with zero
    // cash). A LIMIT order is used because the risk check still values it at its own limit price; a
    // MARKET order without a price is refused instead (see aMarketOrderWithNoMarketPriceIsRefused…).
    @Test
    void aLimitBuyWithNoMarketPriceSkipsTheFundsCheck() {
        MARKET_DATA.stubFor(MarketDataStubs.hasNoPriceFor(UNPRICED_SYMBOL));
        PORTFOLIO.stubFor(PortfolioStubs.hasCash(NO_CASH));

        OrderResponse response = place(TESTER_TOKEN,
                Orders.limit(UNPRICED_SYMBOL, Side.BUY.name(), ONE_SHARE, UNPRICED_LIMIT));

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED.name());
    }

    // ---- risk check: every order type, valued at its worst-case execution price ----

    // The MARKET order has no type (an empty cell is null): the service defaults it and values it at the market price.
    @ParameterizedTest(name = "{0} is risk-checked at {4}")
    @CsvSource(delimiter = '|', value = {
        "MARKET |       |     |     | 100",
        "LIMIT  | LIMIT | 120 |     | 120",
        "STOP   | STOP  |     | 150 | 150"
    })
    void everyOrderTypeIsRiskCheckedAtItsWorstCasePrice(String label, String type, BigDecimal limitPrice,
                                                         BigDecimal stopPrice, BigDecimal riskPrice) {
        BigDecimal quantity = new BigDecimal(RISK_QUANTITY);
        place(TESTER_TOKEN, new OrderRequest(RISK_SYMBOL, Side.BUY.name(), quantity, type, limitPrice, stopPrice));

        RISK.verify(postRequestedFor(urlPathEqualTo(RiskStubs.CHECK))
                .withRequestBody(equalToJson(Json.write(
                        new RiskCheckRequest(RISK_SYMBOL, Side.BUY.name(), quantity, riskPrice)))));
    }

    @Test
    void aMarketOrderWithNoMarketPriceIsRefusedBecauseItCannotBeValued() {
        MARKET_DATA.stubFor(MarketDataStubs.hasNoPriceFor(UNPRICED_SYMBOL));

        String response = api(TESTER_TOKEN).body(Orders.market(UNPRICED_SYMBOL, Side.SELL.name(), ONE_SHARE))
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            softly.assertThat(error.message()).isEqualTo(CANNOT_BE_VALUED_MESSAGE);
            softly.assertThat(error.path()).isEqualTo(ORDERS_PATH);
        });
        RISK.verify(0, postRequestedFor(urlPathEqualTo(RiskStubs.CHECK)));
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // ---- exchange outcomes ----

    // The exchange declines → status REJECTED, and crucially nothing is published (a REJECTED order
    // must never reach the matcher and create a phantom fill).
    @Test
    void exchangeRejectionMarksRejectedAndDoesNotPublish() {
        EXCHANGE.stubFor(ExchangeStubs.declinesOrders());

        OrderResponse response = place(TESTER_TOKEN, Orders.market(DECLINED_SYMBOL, Side.BUY.name(), ONE_SHARE));

        List<String> published = KAFKA_PROBE.valuesWithin(OrderEventPublisher.TOPIC, response.id(), NO_EVENT_WINDOW);
        assertSoftly(softly -> {
            softly.assertThat(response.status()).isEqualTo(OrderStatus.REJECTED.name());
            softly.assertThat(statusOf(response.id())).isEqualTo(OrderStatus.REJECTED);
            softly.assertThat(published).isEmpty();
        });
    }

    // An exchange 5xx is a bad upstream answer, not a bug in order-service: 502 with the stable error
    // body, the transaction rolls back, and nothing is persisted or published (no phantom order/fill).
    @Test
    void anExchangeServerErrorIsABadGatewayWithNoPersistOrPublish() {
        EXCHANGE.stubFor(ExchangeStubs.failsWithServerError());

        String response = api(token(EXCHANGE_FAILURE_OWNER))
                .body(Orders.market(EXCHANGE_FAILURE_SYMBOL, Side.BUY.name(), ONE_SHARE))
                .post(ORDERS_PATH)
                .then().statusCode(HttpStatus.BAD_GATEWAY.value())
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        List<String> published = KAFKA_PROBE.valuesWithin(OrderEventPublisher.TOPIC, EXCHANGE_FAILURE_SYMBOL,
                NO_EVENT_WINDOW);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
            softly.assertThat(error.error()).isEqualTo(HttpStatus.BAD_GATEWAY.getReasonPhrase());
            softly.assertThat(error.message()).isEqualTo(EXCHANGE_FAILURE_MESSAGE);
            softly.assertThat(error.path()).isEqualTo(ORDERS_PATH);
            softly.assertThat(published).isEmpty();
            softly.assertThat(repository.findByOwner(EXCHANGE_FAILURE_OWNER)).isEmpty();
        });
    }

    @Test
    void responseQuantityKeepsTheRequestedPrecision() {
        OrderResponse response = place(TESTER_TOKEN, Orders.market(SYMBOL, Side.BUY.name(), FRACTIONAL_QUANTITY));

        assertThat(response.quantity()).isEqualByComparingTo(new BigDecimal(FRACTIONAL_QUANTITY));
    }
}
