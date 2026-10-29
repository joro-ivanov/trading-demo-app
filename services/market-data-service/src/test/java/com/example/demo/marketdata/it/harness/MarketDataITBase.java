package com.example.demo.marketdata.it.harness;

import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestTimeouts.EVENT_TIMEOUT;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.web.PriceResponse;
import com.example.demo.testing.support.containers.TestContainers;
import com.example.demo.testing.support.fixtures.TradingInstants;
import com.example.demo.testing.support.it.ServiceITBase;
import com.example.demo.testing.support.kafka.KafkaProbe;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Shared harness for the market-data-service integration tests (docs/DESIGN.md §6, §7.2): the real
 * service on a random port ({@link ServiceITBase}) against real Kafka + Redis (Testcontainers), with the
 * exchange's raw WebSocket price feed mocked by WireMock. The only external communication is that feed;
 * Kafka input is produced by the tests themselves. Containers and the feed are singletons shared by every
 * {@code *IT} class (and the cached Spring context). The feed stub is registered before the context
 * starts, because the service subscribes on startup. The service clock is fixed at {@link #NOW}.
 */
public abstract class MarketDataITBase extends ServiceITBase {

    /** Fixed service clock ({@code app.clock.fixed-instant}): every fill is stamped with it. */
    protected static final Instant NOW = TradingInstants.DURING_HOURS;

    static final ConfluentKafkaContainer KAFKA = TestContainers.kafka();
    static final GenericContainer<?> REDIS = TestContainers.redis();

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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainers.redis(registry, REDIS);
        TestContainers.kafka(registry, KAFKA);
        registry.add("exchange.ws-url", () -> "ws://localhost:" + EXCHANGE_FEED.port() + "/exchange/marketdata");
        registry.add("market-data.symbols", () -> MarketDataFixtures.FEED_SYMBOL);
        registry.add("app.clock.fixed-instant", NOW::toString);
    }

    protected static KafkaProbe kafka() {
        return KAFKA_PROBE;
    }

    /** Waits until the feed's price for {@code symbol} is cached and served as {@code price}. */
    protected void awaitCachedPrice(String symbol, String price) {
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> {
            PriceResponse response = api(token(TESTER))
                    .get(MarketDataFixtures.pricePath(symbol))
                    .then().statusCode(200)
                    .extract().as(PriceResponse.class);
            assertThat(response.price()).isEqualByComparingTo(price);
        });
    }
}
