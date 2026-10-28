package com.example.demo.order.it.harness;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.security.Jwt;
import com.example.demo.order.domain.OrderRepository;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.it.harness.stubs.ExchangeStubs;
import com.example.demo.order.it.harness.stubs.MarketDataStubs;
import com.example.demo.order.it.harness.stubs.PortfolioStubs;
import com.example.demo.order.it.harness.stubs.RiskStubs;
import com.example.demo.order.web.OrderResponse;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared harness for the order-service integration tests (docs/DESIGN.md §7.2): the real service on a
 * random port against real Postgres + Confluent cp-kafka (Testcontainers), driven over HTTP with
 * REST-Assured. Only external communication is mocked: the exchange, portfolio-service,
 * market-data-service and risk-service are four WireMock servers (risk allows every order by
 * default). Containers and servers are singletons started once per JVM, so every {@code *IT} class
 * shares them and the cached Spring context. The injected {@code Clock} is a {@link MutableClock}, reset to {@link #DURING_HOURS} before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfig.class)
public abstract class OrderITBase {

    protected static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    /** Wednesday 2024-06-12, 14:00 America/New_York (18:00Z) — inside the 09:30–16:00 session. */
    public static final Instant DURING_HOURS = Instant.parse("2024-06-12T18:00:00Z");

    protected static final String TESTER_NAME = "tester";
    protected static final String ALICE_NAME = "alice";
    protected static final String BOB_NAME = "bob";
    protected static final String TESTER = Jwt.issue(TESTER_NAME, SECRET, 3600);
    protected static final String ALICE = Jwt.issue(ALICE_NAME, SECRET, 3600);
    protected static final String BOB = Jwt.issue(BOB_NAME, SECRET, 3600);

    // Endpoints of the service under test.
    protected static final String ORDERS_PATH = "/orders";
    protected static final String ORDER_PATH = "/orders/{id}";
    protected static final String HISTORY_PATH = "/orders/history";

    /** Upper bound for an asynchronous effect (Kafka event, DB status change) to become visible. */
    protected static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);
    /** How long a "nothing is published" check listens before concluding nothing was. */
    protected static final Duration NO_EVENT_WINDOW = Duration.ofSeconds(5);
    /** A state that must not change is observed for this long (bounded by {@link #STAYS_UNCHANGED_TIMEOUT}). */
    protected static final Duration STAYS_UNCHANGED_FOR = Duration.ofSeconds(3);
    protected static final Duration STAYS_UNCHANGED_TIMEOUT = Duration.ofSeconds(8);

    /** A token for a dedicated owner, so owner-scoped lists hold exactly one test's data. */
    protected static String tokenFor(String owner) {
        return Jwt.issue(owner, SECRET, 3600);
    }

    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.postgres")));
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.kafka")));

    protected static final WireMockServer EXCHANGE = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer PORTFOLIO = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer MARKET_DATA = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer RISK = new WireMockServer(options().dynamicPort());

    protected static final KafkaProbe KAFKA_PROBE;
    /** REST-Assured serializes {@code .body(dto)} and deserializes {@code .as(Dto.class)} with the project's {@code Json}. */
    private static final RestAssuredConfig DTO_MAPPING = RestAssuredConfig.config()
            .objectMapperConfig(new ObjectMapperConfig(new JsonObjectMapper()));

    static {
        POSTGRES.start();
        KAFKA.start();
        EXCHANGE.start();
        PORTFOLIO.start();
        MARKET_DATA.start();
        RISK.start();
        KAFKA_PROBE = new KafkaProbe(KAFKA.getBootstrapServers());
    }

    @LocalServerPort
    int port;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected OrderRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("exchange.base-url", EXCHANGE::baseUrl);
        registry.add("portfolio.base-url", PORTFOLIO::baseUrl);
        registry.add("market-data.base-url", MARKET_DATA::baseUrl);
        registry.add("risk.base-url", RISK::baseUrl);
        // Short read timeouts so slow-dependency cases (stubbed delay ≥ 3× this) fail fast as 503.
        registry.add("exchange.read-timeout", () -> "1s");
        registry.add("portfolio.read-timeout", () -> "1s");
        registry.add("market-data.read-timeout", () -> "1s");
        registry.add("risk.read-timeout", () -> "1s");
    }

    @BeforeEach
    void resetStubsAndClock() {
        EXCHANGE.resetAll();
        PORTFOLIO.resetAll();
        MARKET_DATA.resetAll();
        RISK.resetAll();
        EXCHANGE.stubFor(ExchangeStubs.acceptsOrders());
        PORTFOLIO.stubFor(PortfolioStubs.holdsPlentyOfEverything());
        // Enough cash for every BUY in the suite (incl. the 999999999-share boundary at the default price).
        PORTFOLIO.stubFor(PortfolioStubs.hasCash("1000000000000"));
        MARKET_DATA.stubFor(MarketDataStubs.pricesEverySymbolAt("100"));
        RISK.stubFor(RiskStubs.allowsEverything());
        clock.set(DURING_HOURS);
    }

    /** Every outbound call must hit a stub: a wrong URL/method fails the test instead of passing silently. */
    @AfterEach
    void noUnmatchedOutboundRequests() {
        assertThat(EXCHANGE.findAllUnmatchedRequests()).isEmpty();
        assertThat(PORTFOLIO.findAllUnmatchedRequests()).isEmpty();
        assertThat(MARKET_DATA.findAllUnmatchedRequests()).isEmpty();
        assertThat(RISK.findAllUnmatchedRequests()).isEmpty();
    }

    /** An unauthenticated JSON request to the running service. */
    protected RequestSpecification api() {
        return RestAssured.given()
                .config(DTO_MAPPING)
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    protected RequestSpecification api(String token) {
        return api().header("Authorization", "Bearer " + token);
    }

    /** Places an order (expecting 201) and returns the response — setup for tests about later steps. */
    protected OrderResponse place(String token, Object order) {
        return api(token).body(order).post(ORDERS_PATH)
                .then().statusCode(201)
                .extract().as(OrderResponse.class);
    }

    protected OrderStatus statusOf(String orderId) {
        return repository.findById(UUID.fromString(orderId)).orElseThrow().getStatus();
    }
}
