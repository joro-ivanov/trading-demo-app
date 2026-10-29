package com.example.demo.order.it.harness;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.order.domain.OrderRepository;
import com.example.demo.order.domain.OrderStatus;
import com.example.demo.order.it.harness.stubs.ExchangeStubs;
import com.example.demo.order.it.harness.stubs.MarketDataStubs;
import com.example.demo.order.it.harness.stubs.PortfolioStubs;
import com.example.demo.order.it.harness.stubs.RiskStubs;
import com.example.demo.order.web.OrderResponse;
import com.example.demo.testing.support.containers.TestContainers;
import com.example.demo.testing.support.fixtures.TestUsers;
import com.example.demo.testing.support.fixtures.TradingInstants;
import com.example.demo.testing.support.it.ServiceITBase;
import com.example.demo.testing.support.kafka.KafkaProbe;
import com.example.demo.testing.support.time.MutableClock;
import com.example.demo.testing.support.time.MutableClockConfig;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Shared harness for the order-service integration tests (docs/DESIGN.md §7.2): the real service on a
 * random port ({@link ServiceITBase}) against real Postgres + Confluent cp-kafka (Testcontainers). Only
 * external communication is mocked: the exchange, portfolio-service, market-data-service and risk-service
 * are four WireMock servers (risk allows every order by default). Containers and servers are singletons
 * started once per JVM, so every {@code *IT} class shares them and the cached Spring context. The injected
 * {@code Clock} is a {@link MutableClock}, reset to {@link TradingInstants#DURING_HOURS} before each test.
 */
@Import(MutableClockConfig.class)
public abstract class OrderITBase extends ServiceITBase {

    protected static final String TESTER_TOKEN = token(TestUsers.TESTER);
    protected static final String ALICE_TOKEN = token(TestUsers.ALICE);
    protected static final String BOB_TOKEN = token(TestUsers.BOB);

    // Endpoints of the service under test.
    protected static final String ORDERS_PATH = "/orders";
    protected static final String ORDER_PATH = "/orders/{id}";
    protected static final String HISTORY_PATH = "/orders/history";

    /** How long a "nothing is published" check listens before concluding nothing was. */
    protected static final Duration NO_EVENT_WINDOW = Duration.ofSeconds(5);
    /** A state that must not change is observed for this long (bounded by {@link #STAYS_UNCHANGED_TIMEOUT}). */
    protected static final Duration STAYS_UNCHANGED_FOR = Duration.ofSeconds(3);
    protected static final Duration STAYS_UNCHANGED_TIMEOUT = Duration.ofSeconds(8);

    static final PostgreSQLContainer POSTGRES = TestContainers.postgres();
    static final ConfluentKafkaContainer KAFKA = TestContainers.kafka();

    protected static final WireMockServer EXCHANGE = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer PORTFOLIO = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer MARKET_DATA = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer RISK = new WireMockServer(options().dynamicPort());

    protected static final KafkaProbe KAFKA_PROBE;

    static {
        POSTGRES.start();
        KAFKA.start();
        EXCHANGE.start();
        PORTFOLIO.start();
        MARKET_DATA.start();
        RISK.start();
        KAFKA_PROBE = new KafkaProbe(KAFKA.getBootstrapServers());
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected OrderRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainers.datasource(registry, POSTGRES);
        TestContainers.kafka(registry, KAFKA);
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
        clock.set(TradingInstants.DURING_HOURS);
    }

    /** Every outbound call must hit a stub: a wrong URL/method fails the test instead of passing silently. */
    @AfterEach
    void noUnmatchedOutboundRequests() {
        assertThat(EXCHANGE.findAllUnmatchedRequests()).isEmpty();
        assertThat(PORTFOLIO.findAllUnmatchedRequests()).isEmpty();
        assertThat(MARKET_DATA.findAllUnmatchedRequests()).isEmpty();
        assertThat(RISK.findAllUnmatchedRequests()).isEmpty();
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
