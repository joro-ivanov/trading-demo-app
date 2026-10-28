package com.example.demo.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.messageStubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.sendMessage;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompSession;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.example.demo.order.web.OrderResponse;
import com.example.demo.portfolio.web.FillResponse;
import com.example.demo.portfolio.web.PositionResponse;

/**
 * Cross-service flagship test (docs/DESIGN.md §7.2). Launches order + market-data + portfolio +
 * notification as isolated subprocesses (from their {@code -boot} jars) against shared
 * Testcontainers infra (2× Postgres, cp-kafka, Redis) and a WireMock exchange (REST routing +
 * raw-WS price feed). Asserts the full chain:
 *
 * <pre>
 *   POST /orders (REST) → order-service routes to WireMock REST → orders.submitted (Kafka)
 *     → market-data matches at the WS-fed price → fills (Kafka)
 *       → portfolio builds the position (GET /portfolio)
 *       → notification pushes the fill to a subscribed STOMP client
 * </pre>
 */
@Testcontainers
class OrderToPortfolioFlowIT {

    private static final String CP_KAFKA = "confluentinc/cp-kafka:7.8.0";
    private static final String POSTGRES = "postgres:17-alpine";
    private static final Duration STARTUP = Duration.ofSeconds(90);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer PG_ORDER = new PostgreSQLContainer(POSTGRES)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.postgres-order")));

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer PG_PORTFOLIO = new PostgreSQLContainer(POSTGRES)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.postgres-portfolio")));

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(CP_KAFKA)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.kafka")));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.redis")));

    // Two WireMock servers: mixing a WS message stub and a REST stub on one server breaks the
    // plain REST call (the WS upgrade filter sits on /*), so REST routing and the WS feed are split.
    static final WireMockServer EXCHANGE_REST = new WireMockServer(options().dynamicPort());
    static final WireMockServer EXCHANGE_WS = new WireMockServer(options().dynamicPort());

    static final List<Process> PROCESSES = new ArrayList<>();
    static final String TOKEN = com.example.demo.common.security.Jwt.issue(
            "tester", "demo-secret-demo-secret-demo-secret-1234", 3600);

    static int orderPort;
    static int portfolioPort;
    static int notificationPort;
    static int marketDataPort;

    @BeforeAll
    static void startStack() {
        EXCHANGE_REST.start();
        EXCHANGE_WS.start();
        EXCHANGE_REST.stubFor(post("/exchange/orders")
                .willReturn(okJson("{\"status\":\"ACCEPTED\",\"exchangeOrderId\":\"EX-1\"}")));
        configureFor("localhost", EXCHANGE_WS.port());
        messageStubFor(message()
                .withName("AAPL feed")
                .withBody(WireMock.equalTo("subscribe:AAPL"))
                .willTriggerActions(sendMessage("{\"symbol\":\"AAPL\",\"price\":190.40}").onOriginatingChannel()));

        String kafka = KAFKA.getBootstrapServers();
        orderPort = Stack.freePort();
        portfolioPort = Stack.freePort();
        notificationPort = Stack.freePort();
        marketDataPort = Stack.freePort();

        PROCESSES.add(Stack.launch(jar("marketdata.jar"), "it-market-data.log", marketDataProps(kafka, marketDataPort)));
        PROCESSES.add(Stack.launch(jar("order.jar"), "it-order.log", orderProps(kafka, orderPort)));
        PROCESSES.add(Stack.launch(jar("portfolio.jar"), "it-portfolio.log", portfolioProps(kafka, portfolioPort)));
        PROCESSES.add(Stack.launch(jar("notification.jar"), "it-notification.log", notificationProps(kafka, notificationPort)));

        Stack.awaitListening(orderPort, STARTUP);
        Stack.awaitListening(notificationPort, STARTUP);
        // market-data is ready only once it has consumed the WS feed and cached the price.
        Stack.awaitHttpOk(marketDataPort, "/prices/AAPL", TOKEN, STARTUP);
        // Wait for an *authenticated* 200 (not just an open port) so the security filter chain /
        // JWT decoder are fully ready before the test asserts — avoids a transient 403 under load.
        Stack.awaitHttpOk(portfolioPort, "/portfolio", TOKEN, STARTUP);
    }

    @AfterAll
    static void stopStack() {
        Stack.destroy(PROCESSES);
        EXCHANGE_REST.stop();
        EXCHANGE_WS.stop();
    }

    @Test
    void orderFlowsThroughToPortfolioAndStompClient() throws Exception {
        BlockingQueue<String> fills = new LinkedBlockingQueue<>();
        StompSession session = Stack.subscribe(notificationPort, "/user/queue/fills", TOKEN, fills);

        // tester has no seeded cash — fund the account so the BUY passes the pre-trade funds guard.
        Stack.post(portfolioPort, TOKEN, "/funds/deposit", Map.of("amount", 100000), 200);

        OrderResponse order = Stack.post(orderPort, TOKEN, "/orders",
                Map.of("symbol", "AAPL", "side", "BUY", "quantity", 10), 201, OrderResponse.class);
        assertThat(order.status()).isEqualTo("SUBMITTED");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(Stack.getList(portfolioPort, TOKEN, "/portfolio", PositionResponse.class))
                    .filteredOn(position -> position.symbol().equals("AAPL"))
                    .singleElement()
                    .satisfies(position -> assertThat(position.avgPrice()).isEqualByComparingTo("190.40"));
        });

        // The order is resolved to FILLED in order-service's own DB (it consumes the fill back).
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(Stack.getList(orderPort, TOKEN, "/orders/history", OrderResponse.class))
                    .filteredOn(response -> response.id().equals(order.id()))
                    .singleElement()
                    .satisfies(response -> assertThat(response.status()).isEqualTo("FILLED"));
        });

        String fill = fills.poll(20, TimeUnit.SECONDS);
        session.disconnect();
        assertThat(fill).isNotNull();
        FillResponse fillResponse = Stack.frameAs(fill, FillResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(fillResponse.symbol()).isEqualTo("AAPL");
            softly.assertThat(fillResponse.price()).isEqualByComparingTo("190.40");
        });
    }

    private static String jar(String property) {
        return System.getProperty(property);
    }

    private static Map<String, String> base(String kafka, int port) {
        Map<String, String> props = new HashMap<>();
        props.put("server.port", Integer.toString(port));
        props.put("spring.kafka.bootstrap-servers", kafka);
        return props;
    }

    private static Map<String, String> orderProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put("spring.datasource.url", PG_ORDER.getJdbcUrl());
        props.put("spring.datasource.username", PG_ORDER.getUsername());
        props.put("spring.datasource.password", PG_ORDER.getPassword());
        props.put("exchange.base-url", EXCHANGE_REST.baseUrl());
        props.put("portfolio.base-url", "http://localhost:" + portfolioPort);
        props.put("market-data.base-url", "http://localhost:" + marketDataPort);
        // Deterministic regardless of wall-clock time: disable the working-hours gate for the flow test.
        props.put("trading.market-hours.enabled", "false");
        // The flagship does not boot risk-service; the risk check has its own per-service tests.
        props.put("risk.enabled", "false");
        return props;
    }

    private static Map<String, String> marketDataProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put("spring.data.redis.host", REDIS.getHost());
        props.put("spring.data.redis.port", Integer.toString(REDIS.getMappedPort(6379)));
        props.put("exchange.ws-url", "ws://localhost:" + EXCHANGE_WS.port() + "/exchange/marketdata");
        props.put("market-data.symbols", "AAPL");
        return props;
    }

    private static Map<String, String> portfolioProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put("spring.datasource.url", PG_PORTFOLIO.getJdbcUrl());
        props.put("spring.datasource.username", PG_PORTFOLIO.getUsername());
        props.put("spring.datasource.password", PG_PORTFOLIO.getPassword());
        return props;
    }

    private static Map<String, String> notificationProps(String kafka, int port) {
        return base(kafka, port);
    }
}
