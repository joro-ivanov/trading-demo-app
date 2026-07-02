package com.example.demo.order;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.json.Json;
import com.example.demo.order.domain.OrderRepository;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.events.OrderEventPublisher;
import com.example.demo.order.web.OrderResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Per-service integration test (docs/DESIGN.md §7.2): real Postgres + Confluent cp-kafka via
 * Testcontainers, with the exchange and the portfolio short-sell lookup mocked by WireMock. Drives
 * the controller through the full Spring MVC stack with {@link MockMvc}. The injected {@code Clock}
 * is replaced with a {@link MutableClock} so tests can place orders inside or outside working hours.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(OrderServiceIT.TestClockConfig.class)
class OrderServiceIT {

    private static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    private static final String TOKEN = com.example.demo.common.security.Jwt.issue("tester", SECRET, 300);
    private static final String ALICE = com.example.demo.common.security.Jwt.issue("alice", SECRET, 300);
    private static final String BOB = com.example.demo.common.security.Jwt.issue("bob", SECRET, 300);
    // Wednesday 2024-06-12, 14:00 America/New_York (18:00Z) — inside the 09:30–16:00 session.
    private static final Instant DURING_HOURS = Instant.parse("2024-06-12T18:00:00Z");

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    static final WireMockServer EXCHANGE = new WireMockServer(options().dynamicPort());

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    OrderRepository repository;

    @BeforeAll
    static void startExchange() {
        EXCHANGE.start();
    }

    @AfterAll
    static void stopExchange() {
        EXCHANGE.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("exchange.base-url", EXCHANGE::baseUrl);
        // The short-sell guard's portfolio lookup, the funds guard's balance lookup, and the funds
        // guard's price lookup all hit this WireMock (holdings / funds / prices stubs).
        registry.add("portfolio.base-url", EXCHANGE::baseUrl);
        registry.add("market-data.base-url", EXCHANGE::baseUrl);
    }

    @BeforeEach
    void resetStubsAndClock() {
        EXCHANGE.resetAll();
        EXCHANGE.stubFor(WireMock.post("/exchange/orders")
                .willReturn(okJson("{\"status\":\"ACCEPTED\",\"exchangeOrderId\":\"EX-1\"}")));
        // Default: plenty of holdings so SELL orders pass the short-sell guard.
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathMatching("/portfolio/holdings/.*"))
                .willReturn(okJson("{\"symbol\":\"ANY\",\"quantity\":1000}")));
        // Default: no cached price, so BUY tests that are not about funds remain focused.
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathMatching("/prices/.*"))
                .willReturn(WireMock.notFound()));
        clock.set(DURING_HOURS);
    }

    @Test
    void placeOrderPersistsRoutesAndPublishes() throws Exception {
        Consumer<String, String> consumer = newConsumer();
        consumer.subscribe(List.of(OrderEventPublisher.TOPIC));

        String responseBody = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"HDLNE\",\"side\":\"BUY\",\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        OrderResponse response = Json.read(responseBody, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.symbol()).isEqualTo("HDLNE");
            softly.assertThat(response.type()).isEqualTo("MARKET");
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
        });

        // Assert the persisted row, not just the API response: a placed order is SUBMITTED in the DB.
        String orderId = response.id();
        assertThat(repository.findById(UUID.fromString(orderId)).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SUBMITTED);

        EXCHANGE.verify(postRequestedFor(urlEqualTo("/exchange/orders")));

        List<String> published = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(record -> published.add(record.value()));
            assertThat(published).anyMatch(value ->
                    value.contains("\"symbol\":\"HDLNE\"") && value.contains("\"status\":\"SUBMITTED\""));
        });
        consumer.close();
    }

    // A fill from the matcher resolves the order to FILLED in the DB.
    @Test
    void orderBecomesFilledWhenAFillArrives() throws Exception {
        String orderId = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.SUBMITTED);

        send("fills", "{\"orderId\":\"" + orderId + "\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":1,\"price\":190.40}");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.FILLED));
    }

    // A resting event (the matcher found it non-marketable) resolves the order to PENDING in the DB.
    @Test
    void orderBecomesPendingWhenARestingEventArrives() throws Exception {
        String orderId = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,"
                + "\"type\":\"LIMIT\",\"limitPrice\":0.01}");
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.SUBMITTED);

        send("orders.resting", "{\"orderId\":\"" + orderId + "\"}");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING));
    }

    // Guard: a late fill for a CANCELLED order must not flip it back to FILLED (M18 invariant).
    @Test
    void fillForACancelledOrderLeavesItCancelled() throws Exception {
        String orderId = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        mockMvc.perform(delete("/orders/{id}", orderId).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED);

        send("fills", "{\"orderId\":\"" + orderId + "\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":1,\"price\":190.40}");

        // Give the consumer time to process, then confirm it stayed CANCELLED.
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED));
    }

    // Guard: a resting event for an already-FILLED order is ignored (only SUBMITTED → PENDING).
    @Test
    void restingEventForAFilledOrderLeavesItFilled() throws Exception {
        String orderId = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        send("fills", "{\"orderId\":\"" + orderId + "\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":1,\"price\":190.40}");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.FILLED));

        send("orders.resting", "{\"orderId\":\"" + orderId + "\"}");

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.FILLED));
    }

    @ParameterizedTest(name = "side {0} is accepted")
    @ValueSource(strings = {"BUY", "SELL"})
    void bothSidesAreAcceptedAndSubmitted(String side) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"" + side + "\",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        OrderResponse response = Json.read(body, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.side()).isEqualTo(side);
            softly.assertThat(response.status()).isEqualTo("SUBMITTED");
        });
    }

    // BVA on-point of @Positive (0.0001) plus an absurd size — documents there is no upper cap.
    @ParameterizedTest(name = "quantity {0} is accepted")
    @ValueSource(strings = {"0.0001", "1", "999999999"})
    void acceptsValidQuantityBoundaries(String quantity) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("SUBMITTED");
    }

    @ParameterizedTest(name = "type {0} is accepted")
    @ValueSource(strings = {
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"MARKET\"}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":195.50}",
        "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"type\":\"STOP\",\"stopPrice\":205.00}"
    })
    void acceptsEachOrderType(String body) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(response, OrderResponse.class).status()).isEqualTo("SUBMITTED");
    }

    // EP/BVA invalid partitions: qty 0 (off-point of @Positive), negative, missing; blank/whitespace
    // symbol (@NotBlank); null and unparseable side; LIMIT/STOP missing their required price.
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
    void rejectsInvalidOrderWith400(String body) throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRequestWithoutTokenWith401() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":10}"))
                .andExpect(status().isUnauthorized());
    }

    // An expired token, a forged one (signed with the wrong secret), and a malformed one are all 401.
    @ParameterizedTest(name = "{0} token -> 401")
    @MethodSource("badTokens")
    void rejectsInvalidTokensWith401(String label, String authorization) throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // Ownership comes from the token's subject, never the body — you cannot place an order as someone else.
    @Test
    void orderIsOwnedByTheAuthenticatedSubject() throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"owner\":\"bob\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).owner()).isEqualTo("alice");
    }

    @Test
    void orderHistoryIsScopedToTheAuthenticatedUser() throws Exception {
        String aliceOrderId = placeAsAndReturnId(ALICE, "{\"symbol\":\"NFLX\",\"side\":\"BUY\",\"quantity\":1}");

        String aliceHistory = mockMvc.perform(get("/orders/history").header("Authorization", "Bearer " + ALICE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(aliceHistory).contains(aliceOrderId);

        String bobHistory = mockMvc.perform(get("/orders/history").header("Authorization", "Bearer " + BOB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(bobHistory).doesNotContain(aliceOrderId);
    }

    @Test
    void cannotCancelAnotherUsersOrder() throws Exception {
        String aliceOrderId = placeAsAndReturnId(ALICE, "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");

        // Bob cannot cancel Alice's order — 404 (same as unknown, so existence is not leaked).
        mockMvc.perform(delete("/orders/{id}", aliceOrderId).header("Authorization", "Bearer " + BOB))
                .andExpect(status().isNotFound());
        // Alice can still cancel her own.
        String body = mockMvc.perform(delete("/orders/{id}", aliceOrderId).header("Authorization", "Bearer " + ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
    }

    @Test
    void acceptsOrdersDuringWorkingHours() throws Exception {
        clock.set(Instant.parse("2024-06-12T18:00:00Z")); // Wed 14:00 ET — open

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "market closed at {0}")
    @CsvSource({
        "2024-06-12T07:00:00Z",  // Wed 03:00 ET — before open
        "2024-06-15T18:00:00Z"   // Sat 14:00 ET — weekend, market closed
    })
    void rejectsOrdersOutsideWorkingHours(String instant) throws Exception {
        clock.set(Instant.parse(instant));

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, ErrorResponse.class).message()).isEqualTo("Market is closed");
    }

    @Test
    void rejectsShortSellBeyondHoldingsWith422() throws Exception {
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/portfolio/holdings/AAPL"))
                .willReturn(okJson("{\"symbol\":\"AAPL\",\"quantity\":5}")));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":10}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void allowsSellWithinHoldings() throws Exception {
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/portfolio/holdings/AAPL"))
                .willReturn(okJson("{\"symbol\":\"AAPL\",\"quantity\":50}")));

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("SUBMITTED");
    }

    // BVA on-point: selling exactly the held quantity is allowed (the guard rejects only qty > held).
    @Test
    void allowsSellExactlyEqualToHoldings() throws Exception {
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/portfolio/holdings/AAPL"))
                .willReturn(okJson("{\"symbol\":\"AAPL\",\"quantity\":10}")));

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("SUBMITTED");
    }

    @Test
    void rejectsShortSellWithNoHoldingsWith422() throws Exception {
        EXCHANGE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/portfolio/holdings/AAPL"))
                .willReturn(okJson("{\"symbol\":\"AAPL\",\"quantity\":0}")));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // The exchange declines → status REJECTED, and crucially nothing is published (a REJECTED order
    // must never reach the matcher and create a phantom fill).
    @Test
    void exchangeRejectionMarksRejectedAndDoesNotPublish() throws Exception {
        EXCHANGE.stubFor(WireMock.post("/exchange/orders")
                .willReturn(okJson("{\"status\":\"DECLINED\",\"exchangeOrderId\":\"EX-0\"}")));
        Consumer<String, String> consumer = newConsumer();
        consumer.subscribe(List.of(OrderEventPublisher.TOPIC));

        String responseBody = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"REJSYM\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        OrderResponse response = Json.read(responseBody, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.status()).isEqualTo("REJECTED");
            softly.assertThat(statusOf(response.id())).isEqualTo(OrderStatus.REJECTED);
        });

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        consumer.close();
        List<String> values = new ArrayList<>();
        records.forEach(record -> values.add(record.value()));
        assertThat(values).noneMatch(value -> value.contains("REJSYM"));
    }

    @Test
    void cancelLiveOrderTransitionsToCancelled() throws Exception {
        String id = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");

        String body = mockMvc.perform(delete("/orders/{id}", id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        OrderResponse response = Json.read(body, OrderResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.id()).isEqualTo(id);
            softly.assertThat(response.status()).isEqualTo("CANCELLED");
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void cancelIsIdempotentOnAnAlreadyCancelledOrder() throws Exception {
        String id = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");

        mockMvc.perform(delete("/orders/{id}", id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        String body = mockMvc.perform(delete("/orders/{id}", id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelUnknownOrderReturns404() throws Exception {
        mockMvc.perform(delete("/orders/{id}", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPendingOrderCanBeCancelled() throws Exception {
        String id = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,"
                + "\"type\":\"LIMIT\",\"limitPrice\":0.01}");
        send("orders.resting", "{\"orderId\":\"" + id + "\"}");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.PENDING));

        String body = mockMvc.perform(delete("/orders/{id}", id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, OrderResponse.class).status()).isEqualTo("CANCELLED");
            softly.assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // Filled orders are terminal: cancelling after execution must not rewrite history.
    @Test
    void cancellingAFilledOrderIsRejected() throws Exception {
        String id = placeAndReturnId("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}");
        send("fills", "{\"orderId\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":1,\"price\":190.40}");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED));

        mockMvc.perform(delete("/orders/{id}", id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo(OrderStatus.FILLED);
    }

    // An exchange 5xx is not handled — routing throws (MockMvc rethrows it; a real servlet returns
    // 500), the transaction rolls back, and nothing is persisted or published (no phantom order/fill).
    @Test
    void anExchangeServerErrorRollsBackWithNoPersistOrPublish() throws Exception {
        EXCHANGE.stubFor(WireMock.post("/exchange/orders").willReturn(WireMock.serverError()));
        Consumer<String, String> consumer = newConsumer();
        consumer.subscribe(List.of(OrderEventPublisher.TOPIC));

        assertThatThrownBy(() -> mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"EXFAIL\",\"side\":\"BUY\",\"quantity\":1}")))
                .hasMessageContaining("500");

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        consumer.close();
        List<String> values = new ArrayList<>();
        records.forEach(record -> values.add(record.value()));
        assertSoftly(softly -> {
            softly.assertThat(values).noneMatch(value -> value.contains("EXFAIL"));
            softly.assertThat(repository.findByOwner("tester")).noneMatch(order -> order.getSymbol().equals("EXFAIL"));
        });
    }

    // ---- pre-trade funds guard (BUY) ----

    @Test
    void aBuyIsRejectedWhenFundsAreInsufficient() throws Exception {
        EXCHANGE.stubFor(WireMock.get("/prices/FUND").willReturn(okJson("{\"symbol\":\"FUND\",\"price\":100}")));
        EXCHANGE.stubFor(WireMock.get("/funds/balance").willReturn(okJson("{\"cash\":50}")));

        // cost = 1 × 100 = 100 > 50 available → rejected before routing
        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"FUND\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aBuyIsAcceptedWhenFundsAreSufficient() throws Exception {
        EXCHANGE.stubFor(WireMock.get("/prices/FUND").willReturn(okJson("{\"symbol\":\"FUND\",\"price\":100}")));
        EXCHANGE.stubFor(WireMock.get("/funds/balance").willReturn(okJson("{\"cash\":1000}")));

        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"FUND\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    // No cached price → the order can't be valued → funds guard skipped (accepted even with zero cash).
    @Test
    void aBuyWithNoCachedPriceSkipsTheFundsCheck() throws Exception {
        EXCHANGE.stubFor(WireMock.get("/funds/balance").willReturn(okJson("{\"cash\":0}")));

        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"NOPX\",\"side\":\"BUY\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void orderHistoryIncludesAPlacedOrder() throws Exception {
        String id = placeAndReturnId("{\"symbol\":\"NFLX\",\"side\":\"BUY\",\"quantity\":2}");

        String history = mockMvc.perform(get("/orders/history").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(history).contains(id).contains("NFLX");
    }

    private String placeAndReturnId(String body) throws Exception {
        return placeAsAndReturnId(TOKEN, body);
    }

    private String placeAsAndReturnId(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Json.read(response, OrderResponse.class).id();
    }

    static Stream<Arguments> badTokens() {
        return Stream.of(
                Arguments.of("expired",
                        "Bearer " + com.example.demo.common.security.Jwt.issue("tester", SECRET, -60)),
                Arguments.of("forged",
                        "Bearer " + com.example.demo.common.security.Jwt.issue(
                                "tester", "wrong-secret-wrong-secret-wrong-secret-99", 300)),
                Arguments.of("malformed", "Bearer not-a-jwt"));
    }

    private OrderStatus statusOf(String orderId) {
        return repository.findById(UUID.fromString(orderId)).orElseThrow().getStatus();
    }

    private static void send(String topic, String payload) {
        Producer<String, String> producer = newProducer();
        producer.send(new ProducerRecord<>(topic, "k", payload));
        producer.flush();
        producer.close();
    }

    private static Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }

    private record ErrorResponse(String message) {
    }

    private static Consumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    /** A clock whose instant the test sets, to place orders inside or outside working hours. */
    static class MutableClock extends Clock {
        private volatile Instant instant = DURING_HOURS;

        void set(Instant value) {
            this.instant = value;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
