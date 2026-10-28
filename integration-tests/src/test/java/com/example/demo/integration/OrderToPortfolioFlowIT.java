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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
import com.example.demo.common.json.Json;
import com.example.demo.marketdata.model.PriceTick;
import com.example.demo.order.domain.Side;
import com.example.demo.order.exchange.ExchangeAck;
import com.example.demo.order.web.OrderResponse;
import com.example.demo.order.web.PlaceOrderRequest;
import com.example.demo.portfolio.web.FundsDtos.FundsRequest;
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
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;
    private static final Duration STARTUP = Duration.ofSeconds(90);
    private static final Duration FLOW_TIMEOUT = Duration.ofSeconds(30);
    private static final long FILL_FRAME_TIMEOUT_SECONDS = 20;

    private static final String SUBJECT = "tester";
    private static final String JWT_SECRET = "demo-secret-demo-secret-demo-secret-1234";
    private static final long TOKEN_TTL_SECONDS = 3600;

    private static final String LOCALHOST = "localhost";
    private static final String HTTP_LOCALHOST = "http://" + LOCALHOST + ":";
    private static final String WS_LOCALHOST = "ws://" + LOCALHOST + ":";

    // The exchange (WireMock): REST order routing and the raw-WS price feed.
    private static final String EXCHANGE_ORDERS_PATH = "/exchange/orders";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String EXCHANGE_ORDER_ID = "EX-1";
    private static final ExchangeAck EXCHANGE_ACK = new ExchangeAck(ACCEPTED, EXCHANGE_ORDER_ID);
    private static final String EXCHANGE_FEED_PATH = "/exchange/marketdata";
    private static final String FEED_STUB_NAME = "AAPL feed";
    private static final String SYMBOL = "AAPL";
    private static final String SUBSCRIBE_FRAME = "subscribe:" + SYMBOL;
    private static final String FEED_PRICE = "190.40";
    private static final PriceTick FEED_TICK = new PriceTick(SYMBOL, new BigDecimal(FEED_PRICE));

    // Service jars (system properties set by the pom) and their process logs.
    private static final String MARKET_DATA_JAR = "marketdata.jar";
    private static final String ORDER_JAR = "order.jar";
    private static final String PORTFOLIO_JAR = "portfolio.jar";
    private static final String NOTIFICATION_JAR = "notification.jar";
    private static final String MARKET_DATA_LOG = "it-market-data.log";
    private static final String ORDER_LOG = "it-order.log";
    private static final String PORTFOLIO_LOG = "it-portfolio.log";
    private static final String NOTIFICATION_LOG = "it-notification.log";

    // HTTP / STOMP endpoints.
    private static final String PRICE_PATH = "/prices/" + SYMBOL;
    private static final String PORTFOLIO_PATH = "/portfolio";
    private static final String DEPOSIT_PATH = "/funds/deposit";
    private static final String ORDERS_PATH = "/orders";
    private static final String ORDER_HISTORY_PATH = "/orders/history";
    private static final String FILLS_QUEUE = "/user/queue/fills";

    // The order under test and its expected outcome.
    private static final int DEPOSIT_AMOUNT = 100000;
    private static final int ORDER_QUANTITY = 10;
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final String SUBMITTED = "SUBMITTED";
    private static final String FILLED = "FILLED";

    // Spring properties passed to the service processes.
    private static final String SERVER_PORT = "server.port";
    private static final String KAFKA_BOOTSTRAP = "spring.kafka.bootstrap-servers";
    private static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String EXCHANGE_BASE_URL = "exchange.base-url";
    private static final String PORTFOLIO_BASE_URL = "portfolio.base-url";
    private static final String MARKET_DATA_BASE_URL = "market-data.base-url";
    private static final String MARKET_HOURS_ENABLED = "trading.market-hours.enabled";
    private static final String RISK_ENABLED = "risk.enabled";
    private static final String DISABLED = "false";
    private static final String REDIS_HOST = "spring.data.redis.host";
    private static final String REDIS_PORT_PROPERTY = "spring.data.redis.port";
    private static final String EXCHANGE_WS_URL = "exchange.ws-url";
    private static final String MARKET_DATA_SYMBOLS = "market-data.symbols";

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
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(REDIS_PORT)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.redis")));

    // Two WireMock servers: mixing a WS message stub and a REST stub on one server breaks the
    // plain REST call (the WS upgrade filter sits on /*), so REST routing and the WS feed are split.
    static final WireMockServer EXCHANGE_REST = new WireMockServer(options().dynamicPort());
    static final WireMockServer EXCHANGE_WS = new WireMockServer(options().dynamicPort());

    static final List<Process> PROCESSES = new ArrayList<>();
    static final String TOKEN = com.example.demo.common.security.Jwt.issue(SUBJECT, JWT_SECRET, TOKEN_TTL_SECONDS);

    static int orderPort;
    static int portfolioPort;
    static int notificationPort;
    static int marketDataPort;

    @BeforeAll
    static void startStack() {
        EXCHANGE_REST.start();
        EXCHANGE_WS.start();
        EXCHANGE_REST.stubFor(post(EXCHANGE_ORDERS_PATH).willReturn(okJson(Json.write(EXCHANGE_ACK))));
        configureFor(LOCALHOST, EXCHANGE_WS.port());
        messageStubFor(message()
                .withName(FEED_STUB_NAME)
                .withBody(WireMock.equalTo(SUBSCRIBE_FRAME))
                .willTriggerActions(sendMessage(Json.write(FEED_TICK)).onOriginatingChannel()));

        String kafka = KAFKA.getBootstrapServers();
        orderPort = Stack.freePort();
        portfolioPort = Stack.freePort();
        notificationPort = Stack.freePort();
        marketDataPort = Stack.freePort();

        PROCESSES.add(Stack.launch(jar(MARKET_DATA_JAR), MARKET_DATA_LOG, marketDataProps(kafka, marketDataPort)));
        PROCESSES.add(Stack.launch(jar(ORDER_JAR), ORDER_LOG, orderProps(kafka, orderPort)));
        PROCESSES.add(Stack.launch(jar(PORTFOLIO_JAR), PORTFOLIO_LOG, portfolioProps(kafka, portfolioPort)));
        PROCESSES.add(Stack.launch(jar(NOTIFICATION_JAR), NOTIFICATION_LOG, notificationProps(kafka, notificationPort)));

        Stack.awaitListening(orderPort, STARTUP);
        Stack.awaitListening(notificationPort, STARTUP);
        // market-data is ready only once it has consumed the WS feed and cached the price.
        Stack.awaitHttpOk(marketDataPort, PRICE_PATH, TOKEN, STARTUP);
        // Wait for an *authenticated* 200 (not just an open port) so the security filter chain /
        // JWT decoder are fully ready before the test asserts — avoids a transient 403 under load.
        Stack.awaitHttpOk(portfolioPort, PORTFOLIO_PATH, TOKEN, STARTUP);
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
        StompSession session = Stack.subscribe(notificationPort, FILLS_QUEUE, TOKEN, fills);

        // tester has no seeded cash — fund the account so the BUY passes the pre-trade funds guard.
        Stack.post(portfolioPort, TOKEN, DEPOSIT_PATH, new FundsRequest(BigDecimal.valueOf(DEPOSIT_AMOUNT)), OK);

        OrderResponse order = Stack.post(orderPort, TOKEN, ORDERS_PATH,
                new PlaceOrderRequest(SYMBOL, Side.BUY, BigDecimal.valueOf(ORDER_QUANTITY), null, null, null),
                CREATED, OrderResponse.class);
        assertThat(order.status()).isEqualTo(SUBMITTED);

        await().atMost(FLOW_TIMEOUT).untilAsserted(() -> {
            assertThat(Stack.getList(portfolioPort, TOKEN, PORTFOLIO_PATH, PositionResponse.class))
                    .filteredOn(position -> position.symbol().equals(SYMBOL))
                    .singleElement()
                    .satisfies(position -> assertThat(position.avgPrice()).isEqualByComparingTo(FEED_PRICE));
        });

        // The order is resolved to FILLED in order-service's own DB (it consumes the fill back).
        await().atMost(FLOW_TIMEOUT).untilAsserted(() -> {
            assertThat(Stack.getList(orderPort, TOKEN, ORDER_HISTORY_PATH, OrderResponse.class))
                    .filteredOn(response -> response.id().equals(order.id()))
                    .singleElement()
                    .satisfies(response -> assertThat(response.status()).isEqualTo(FILLED));
        });

        String fill = Stack.nextFrame(fills, FILL_FRAME_TIMEOUT_SECONDS);
        session.disconnect();
        FillResponse fillResponse = Stack.frameAs(fill, FillResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(fillResponse.symbol()).isEqualTo(SYMBOL);
            softly.assertThat(fillResponse.price()).isEqualByComparingTo(FEED_PRICE);
        });
    }

    private static String jar(String property) {
        return System.getProperty(property);
    }

    private static Map<String, String> base(String kafka, int port) {
        Map<String, String> props = new HashMap<>();
        props.put(SERVER_PORT, Integer.toString(port));
        props.put(KAFKA_BOOTSTRAP, kafka);
        return props;
    }

    private static Map<String, String> orderProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put(DATASOURCE_URL, PG_ORDER.getJdbcUrl());
        props.put(DATASOURCE_USERNAME, PG_ORDER.getUsername());
        props.put(DATASOURCE_PASSWORD, PG_ORDER.getPassword());
        props.put(EXCHANGE_BASE_URL, EXCHANGE_REST.baseUrl());
        props.put(PORTFOLIO_BASE_URL, HTTP_LOCALHOST + portfolioPort);
        props.put(MARKET_DATA_BASE_URL, HTTP_LOCALHOST + marketDataPort);
        // Deterministic regardless of wall-clock time: disable the working-hours gate for the flow test.
        props.put(MARKET_HOURS_ENABLED, DISABLED);
        // The flagship does not boot risk-service; the risk check has its own per-service tests.
        props.put(RISK_ENABLED, DISABLED);
        return props;
    }

    private static Map<String, String> marketDataProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put(REDIS_HOST, REDIS.getHost());
        props.put(REDIS_PORT_PROPERTY, Integer.toString(REDIS.getMappedPort(REDIS_PORT)));
        props.put(EXCHANGE_WS_URL, WS_LOCALHOST + EXCHANGE_WS.port() + EXCHANGE_FEED_PATH);
        props.put(MARKET_DATA_SYMBOLS, SYMBOL);
        return props;
    }

    private static Map<String, String> portfolioProps(String kafka, int port) {
        Map<String, String> props = base(kafka, port);
        props.put(DATASOURCE_URL, PG_PORTFOLIO.getJdbcUrl());
        props.put(DATASOURCE_USERNAME, PG_PORTFOLIO.getUsername());
        props.put(DATASOURCE_PASSWORD, PG_PORTFOLIO.getPassword());
        return props;
    }

    private static Map<String, String> notificationProps(String kafka, int port) {
        return base(kafka, port);
    }
}
