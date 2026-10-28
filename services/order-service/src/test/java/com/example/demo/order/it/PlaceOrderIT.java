package com.example.demo.order.it;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Duration;
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

import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.events.OrderEventPublisher;
import com.example.demo.order.it.harness.ApiError;
import com.example.demo.order.it.harness.OrderITBase;
import com.example.demo.order.it.harness.OrderSubmittedMessage;
import com.example.demo.order.it.harness.stubs.ExchangeStubs;
import com.example.demo.order.it.harness.stubs.MarketDataStubs;
import com.example.demo.order.it.harness.stubs.PortfolioStubs;
import com.example.demo.order.web.OrderResponse;

/**
 * {@code POST /orders}: validation, authentication, ownership, the working-hours gate, the pre-trade
 * short-sell (SELL) and funds (BUY) guards, routing to the exchange, persistence and the
 * {@code orders.submitted} event.
 */
class PlaceOrderIT extends OrderITBase {

    // ---- happy path: persisted, routed, published ----

    @Test
    void placeOrderPersistsRoutesAndPublishes() {
        String body = api(TESTER)
                .body("{\"symbol\":\"HDLNE\",\"side\":\"BUY\",\"quantity\":10}")
                .post("/orders")
                .then().statusCode(201)
                .extract().asString();

        OrderResponse response = Json.read(body, OrderResponse.class);
        OrderSubmittedMessage event = Json.read(
                KAFKA_PROBE.awaitValue(OrderEventPublisher.TOPIC, response.id(), Duration.ofSeconds(20)),
                OrderSubmittedMessage.class);
        assertSoftly(softly -> {
            softly.assertThat(response.symbol()).isEqualTo("HDLNE");
            softly.assertThat(response.side()).isEqualTo("BUY");
            softly.assertThat(response.quantity()).isEqualByComparingTo("10");
            softly.assertThat(response.type()).isEqualTo("MARKET");
            softly.assertThat(response.owner()).isEqualTo("tester");
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
            // The persisted row, not just the API response.
            softly.assertThat(statusOf(response.id())).isEqualTo(OrderStatus.SUBMITTED);
            softly.assertThat(event.id()).isEqualTo(response.id());
            softly.assertThat(event.symbol()).isEqualTo("HDLNE");
            softly.assertThat(event.side()).isEqualTo("BUY");
            softly.assertThat(event.quantity()).isEqualByComparingTo("10");
            softly.assertThat(event.type()).isEqualTo("MARKET");
            softly.assertThat(event.status()).isEqualTo("SUBMITTED");
            softly.assertThat(event.owner()).isEqualTo("tester");
        });
        EXCHANGE.verify(postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS))
                .withRequestBody(equalToJson("{\"symbol\":\"HDLNE\",\"side\":\"BUY\",\"quantity\":10}")));
    }

    @ParameterizedTest(name = "side {0} is accepted")
    @ValueSource(strings = {"BUY", "SELL"})
    void bothSidesAreAcceptedAndSubmitted(String side) {
        OrderResponse response = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"" + side + "\",\"quantity\":3}");

        assertSoftly(softly -> {
            softly.assertThat(response.side()).isEqualTo(side);
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
        });
    }

    // BVA on-point of @Positive (0.0001) plus an absurd size — documents there is no upper cap.
    @ParameterizedTest(name = "quantity {0} is accepted")
    @ValueSource(strings = {"0.0001", "1", "999999999"})
    void acceptsValidQuantityBoundaries(String quantity) {
        OrderResponse response = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":" + quantity + "}");

        assertSoftly(softly -> {
            softly.assertThat(response.quantity()).isEqualByComparingTo(quantity);
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
        });
    }

    @ParameterizedTest(name = "type {0} is accepted")
    @CsvSource(delimiter = '|', value = {
        "MARKET | {\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"MARKET\"}",
        "LIMIT  | {\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":195.50}",
        "STOP   | {\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"STOP\",\"stopPrice\":205.00}"
    })
    void acceptsEachOrderType(String type, String body) {
        OrderResponse response = place(TESTER, body);

        assertSoftly(softly -> {
            softly.assertThat(response.type()).isEqualTo(type);
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
        });
    }

    // ---- request validation ----

    // EP/BVA invalid partitions: qty 0 (off-point of @Positive), negative, missing; blank/whitespace
    // symbol (@NotBlank); null and unparseable side; LIMIT/STOP missing or non-positive required price.
    @ParameterizedTest(name = "{0} -> 400")
    @ValueSource(strings = {
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":0}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":-5}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":null}",
        "{\"symbol\":\"\",\"side\":\"BUY\",\"quantity\":10}",
        "{\"symbol\":\"   \",\"side\":\"BUY\",\"quantity\":10}",
        "{\"symbol\":\"AAPL\",\"side\":null,\"quantity\":10}",
        "{\"symbol\":\"AAPL\",\"side\":\"HODL\",\"quantity\":10}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"LIMIT\"}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"STOP\"}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":0}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":-5}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"STOP\",\"stopPrice\":0}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"STOP\",\"stopPrice\":-5}"
    })
    void rejectsInvalidOrderWith400(String body) {
        String response = api(TESTER).body(body).post("/orders")
                .then().statusCode(400)
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(400);
            softly.assertThat(error.message()).isEqualTo("Invalid request content.");
            softly.assertThat(error.path()).isEqualTo("/orders");
        });
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // ---- authentication & ownership ----

    @Test
    void rejectsRequestWithoutTokenWith401() {
        api().body("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":10}").post("/orders")
                .then().statusCode(401);

        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // An expired token, a forged one (signed with the wrong secret), and a malformed one are all 401.
    @ParameterizedTest(name = "{0} token -> 401")
    @MethodSource("badTokens")
    void rejectsInvalidTokensWith401(String label, String authorization) {
        api().header("Authorization", authorization)
                .body("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}")
                .post("/orders")
                .then().statusCode(401);

        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    static Stream<Arguments> badTokens() {
        return Stream.of(
                Arguments.of("expired", "Bearer " + Jwt.issue("tester", SECRET, -60)),
                Arguments.of("forged", "Bearer " + Jwt.issue("tester", "wrong-secret-wrong-secret-wrong-secret-99", 300)),
                Arguments.of("malformed", "Bearer not-a-jwt"));
    }

    // Ownership comes from the token's subject, never the body — you cannot place an order as someone else.
    @Test
    void orderIsOwnedByTheAuthenticatedSubject() {
        OrderResponse response = place(ALICE, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"owner\":\"bob\"}");

        assertSoftly(softly -> {
            softly.assertThat(response.owner()).isEqualTo("alice");
            softly.assertThat(repository.findById(UUID.fromString(response.id())).orElseThrow().getOwner())
                    .isEqualTo("alice");
        });
    }

    // ---- working-hours gate (injected clock) ----

    @Test
    void acceptsOrdersDuringWorkingHours() {
        clock.set(Instant.parse("2024-06-12T18:00:00Z")); // Wed 14:00 ET — open

        OrderResponse response = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    @ParameterizedTest(name = "market closed at {0}")
    @CsvSource({
        "2024-06-12T07:00:00Z",  // Wed 03:00 ET — before open
        "2024-06-15T18:00:00Z"   // Sat 14:00 ET — weekend, market closed
    })
    void rejectsOrdersOutsideWorkingHours(String instant) {
        clock.set(Instant.parse(instant));

        String response = api(TESTER).body("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}").post("/orders")
                .then().statusCode(422)
                .extract().asString();

        ApiError error = Json.read(response, ApiError.class);
        assertSoftly(softly -> {
            softly.assertThat(error.status()).isEqualTo(422);
            softly.assertThat(error.message()).isEqualTo("Market is closed");
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
        PORTFOLIO.stubFor(PortfolioStubs.holds("AAPL", held));

        String response = api(TESTER).body("{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":" + quantity + "}")
                .post("/orders")
                .then().statusCode(422)
                .extract().asString();

        assertThat(Json.read(response, ApiError.class).message())
                .isEqualTo("Insufficient holdings to sell " + quantity + " AAPL (held " + held + ")");
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    // BVA: well within holdings, and on-point (selling exactly the held quantity — the guard rejects only qty > held).
    @ParameterizedTest(name = "held {0}, sell 10 -> 201")
    @ValueSource(strings = {"50", "10"})
    void allowsSellWithinHoldings(String held) {
        PORTFOLIO.stubFor(PortfolioStubs.holds("AAPL", held));

        OrderResponse response = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":10}");

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    // ---- funds guard (BUY) — market-data price × quantity vs portfolio cash ----

    @Test
    void aBuyIsRejectedWhenFundsAreInsufficient() {
        MARKET_DATA.stubFor(MarketDataStubs.hasPrice("FUND", "100"));
        PORTFOLIO.stubFor(PortfolioStubs.hasCash("50"));

        // cost = 1 × 100 = 100 > 50 available → rejected before routing
        String response = api(TESTER).body("{\"symbol\":\"FUND\",\"side\":\"BUY\",\"quantity\":1}").post("/orders")
                .then().statusCode(422)
                .extract().asString();

        assertThat(Json.read(response, ApiError.class).message())
                .isEqualTo("Insufficient funds to buy 1 FUND (cost 100, available 50)");
        EXCHANGE.verify(0, postRequestedFor(urlPathEqualTo(ExchangeStubs.ORDERS)));
    }

    @Test
    void aBuyIsAcceptedWhenFundsAreSufficient() {
        MARKET_DATA.stubFor(MarketDataStubs.hasPrice("FUND", "100"));
        PORTFOLIO.stubFor(PortfolioStubs.hasCash("1000"));

        OrderResponse response = place(TESTER, "{\"symbol\":\"FUND\",\"side\":\"BUY\",\"quantity\":1}");

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    // No cached price → the order can't be valued → funds guard skipped (accepted even with zero cash).
    @Test
    void aBuyWithNoCachedPriceSkipsTheFundsCheck() {
        PORTFOLIO.stubFor(PortfolioStubs.hasCash("0"));

        OrderResponse response = place(TESTER, "{\"symbol\":\"NOPX\",\"side\":\"BUY\",\"quantity\":1}");

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    // ---- exchange outcomes ----

    // The exchange declines → status REJECTED, and crucially nothing is published (a REJECTED order
    // must never reach the matcher and create a phantom fill).
    @Test
    void exchangeRejectionMarksRejectedAndDoesNotPublish() {
        EXCHANGE.stubFor(ExchangeStubs.declinesOrders());

        OrderResponse response = place(TESTER, "{\"symbol\":\"REJSYM\",\"side\":\"BUY\",\"quantity\":1}");

        List<String> published = KAFKA_PROBE.valuesWithin(OrderEventPublisher.TOPIC, response.id(), Duration.ofSeconds(5));
        assertSoftly(softly -> {
            softly.assertThat(response.status()).isEqualTo("REJECTED");
            softly.assertThat(statusOf(response.id())).isEqualTo(OrderStatus.REJECTED);
            softly.assertThat(published).isEmpty();
        });
    }

    // An exchange 5xx is not handled by the service: the request fails with 500, the transaction rolls
    // back, and nothing is persisted or published (no phantom order/fill).
    @Test
    void anExchangeServerErrorRollsBackWithNoPersistOrPublish() {
        EXCHANGE.stubFor(ExchangeStubs.failsWithServerError());

        api(tokenFor("exchange-5xx")).body("{\"symbol\":\"EXFAIL\",\"side\":\"BUY\",\"quantity\":1}").post("/orders")
                .then().statusCode(500);

        List<String> published = KAFKA_PROBE.valuesWithin(OrderEventPublisher.TOPIC, "EXFAIL", Duration.ofSeconds(5));
        assertSoftly(softly -> {
            softly.assertThat(published).isEmpty();
            softly.assertThat(repository.findByOwner("exchange-5xx")).isEmpty();
        });
    }

    @Test
    void responseQuantityKeepsTheRequestedPrecision() {
        OrderResponse response = place(TESTER, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":2.5}");

        assertThat(response.quantity()).isEqualByComparingTo(new BigDecimal("2.5"));
    }
}
