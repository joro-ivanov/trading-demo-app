package com.example.demo.marketdata.it.harness;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.web.PriceResponse;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared harness for the market-data-service integration tests (docs/DESIGN.md §6, §7.2): the real
 * service on a random port against real Kafka + Redis (Testcontainers), with the exchange's raw
 * WebSocket price feed mocked by WireMock. The only external communication is that feed; Kafka
 * input is produced by the tests themselves. Containers and the feed are singletons shared by every
 * {@code *IT} class (and the cached Spring context). The feed stub is registered before the context
 * starts, because the service subscribes on startup. The service clock is fixed at {@link #NOW}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MarketDataITBase {

    protected static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    /** Fixed service clock ({@code app.clock.fixed-instant}): every fill is stamped with it. */
    protected static final Instant NOW = Instant.parse("2024-06-12T18:00:00Z");

    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** WS-only WireMock (REST and WS stubs must not share a server — cookbook §2). */
    static final WireMockServer EXCHANGE_FEED = new WireMockServer(options().dynamicPort());

    private static final KafkaProbe KAFKA_PROBE;

    static {
        KAFKA.start();
        REDIS.start();
        EXCHANGE_FEED.start();
        ExchangeFeedStubs.aaplPriceBurstOnSubscribe(EXCHANGE_FEED);
        KAFKA_PROBE = new KafkaProbe(KAFKA.getBootstrapServers());
    }

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("exchange.ws-url", () -> "ws://localhost:" + EXCHANGE_FEED.port() + "/exchange/marketdata");
        registry.add("market-data.symbols", () -> "AAPL");
        registry.add("app.clock.fixed-instant", NOW::toString);
    }

    protected static KafkaProbe kafka() {
        return KAFKA_PROBE;
    }

    /** An unauthenticated JSON request to the running service. */
    protected RequestSpecification api() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON);
    }

    /** A JSON request carrying {@code Authorization: Bearer <token>}. */
    protected RequestSpecification api(String token) {
        return api().header("Authorization", "Bearer " + token);
    }

    /** A valid HS256 token for {@code subject}, signed with the shared secret (5 minutes). */
    protected static String validToken(String subject) {
        return Jwt.issue(subject, SECRET, 300);
    }

    /** Waits until the feed's price for {@code symbol} is cached and served as {@code price}. */
    protected void awaitCachedPrice(String symbol, String price) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String body = api(validToken("tester"))
                    .get("/prices/" + symbol)
                    .then().statusCode(200)
                    .extract().asString();
            assertThat(Json.read(body, PriceResponse.class).price()).isEqualByComparingTo(price);
        });
    }
}
