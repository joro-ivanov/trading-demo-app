package com.example.demo.marketdata;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.messageStubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.sendMessage;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.example.demo.marketdata.events.PriceTickPublisher;

/**
 * Headline WS-mock integration test (docs/DESIGN.md §6, §7.2): the market-data-service connects
 * to a WireMock <b>raw WebSocket</b> exchange feed, which replies to {@code subscribe:AAPL} with
 * a burst of price frames. Verifies the service republishes them to Kafka ({@code price.ticks})
 * and serves the latest from Redis via {@code GET /prices/AAPL}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketDataServiceIT {

    private static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";
    private static final String TOKEN = com.example.demo.common.security.Jwt.issue("tester", SECRET, 300);

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final WireMockServer EXCHANGE = new WireMockServer(options().dynamicPort());

    static {
        EXCHANGE.start();
    }

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void stubExchangeFeed() {
        configureFor("localhost", EXCHANGE.port());
        messageStubFor(message()
                .withName("AAPL price feed")
                .withBody(equalTo("subscribe:AAPL"))
                .willTriggerActions(
                        sendMessage("{\"symbol\":\"AAPL\",\"price\":190.10}").onOriginatingChannel(),
                        sendMessage("{\"symbol\":\"AAPL\",\"price\":190.40}").onOriginatingChannel()));
    }

    @AfterAll
    static void stopExchange() {
        EXCHANGE.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("exchange.ws-url", () -> "ws://localhost:" + EXCHANGE.port() + "/exchange/marketdata");
        registry.add("market-data.symbols", () -> "AAPL");
    }

    @Test
    void republishesExchangeFeedToKafkaAndServesLatestPrice() {
        Consumer<String, String> consumer = newConsumer();
        consumer.subscribe(List.of(PriceTickPublisher.TOPIC));

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20), 2);
        consumer.close();

        assertThat(records.count()).isEqualTo(2);
        String joined = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .collect(Collectors.joining("|"));
        assertThat(joined).contains("190.10").contains("190.40");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String body = mockMvc.perform(get("/prices/AAPL").header("Authorization", "Bearer " + TOKEN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains("\"symbol\":\"AAPL\"").contains("190.40");
        });
    }

    @Test
    void orderForAKnownSymbolEmitsExactlyOneFullFill() throws Exception {
        awaitPrice("AAPL", "190.40");

        Consumer<String, String> fills = newConsumer();
        fills.subscribe(List.of("fills"));
        produceOrder("ord-full", "{\"id\":\"ord-full\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":7,\"status\":\"SUBMITTED\"}");

        List<String> all = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            fills.poll(Duration.ofMillis(500)).forEach(record -> all.add(record.value()));
            assertThat(all).filteredOn(value -> value.contains("ord-full")).hasSize(1)
                    .allMatch(value -> value.contains("\"quantity\":7") && value.contains("190.40"));
        });
        fills.close();
    }

    // All four directions, market = 190.40: BUY LIMIT fills if market ≤ limit; SELL LIMIT if market ≥
    // limit; BUY STOP if market ≥ stop; SELL STOP if market ≤ stop. The "==190.40" rows pin the
    // inclusive boundary (≤/≥, not </>). Every fill must be at the MARKET price (so a LIMIT gets
    // price improvement — it never fills at its stated limit).
    @ParameterizedTest(name = "{0} {1} {2}={3} fills at market 190.40")
    @CsvSource({
        "LIMIT,BUY,limitPrice,190.40", "LIMIT,BUY,limitPrice,200",
        "LIMIT,SELL,limitPrice,190.40", "LIMIT,SELL,limitPrice,180",
        "STOP,BUY,stopPrice,190.40", "STOP,BUY,stopPrice,180",
        "STOP,SELL,stopPrice,190.40", "STOP,SELL,stopPrice,200"
    })
    void marketableTypedOrderFillsAtMarketPrice(String type, String side, String field, String price)
            throws Exception {
        awaitPrice("AAPL", "190.40");
        String id = "ord-fill-" + type + side + price.replace(".", "");
        Consumer<String, String> fills = newConsumer();
        fills.subscribe(List.of("fills"));
        produceOrder(id, "{\"id\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"" + side + "\",\"quantity\":2,"
                + "\"type\":\"" + type + "\",\"" + field + "\":" + price + ",\"status\":\"SUBMITTED\"}");

        List<String> all = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            fills.poll(Duration.ofMillis(500)).forEach(record -> all.add(record.value()));
            assertThat(all).filteredOn(value -> value.contains(id)).hasSize(1)
                    .allMatch(value -> value.contains("190.40"));
        });
        fills.close();
    }

    // The mirror: a non-marketable LIMIT/STOP on each side emits NO fill. The SELL STOP row is the
    // "stop that never triggers is silently dropped" case (a stop-loss that didn't fire).
    @ParameterizedTest(name = "{0} {1} {2}={3} does not fill at 190.40")
    @CsvSource({
        "LIMIT,BUY,limitPrice,180", "LIMIT,SELL,limitPrice,200",
        "STOP,BUY,stopPrice,200", "STOP,SELL,stopPrice,180"
    })
    void nonMarketableTypedOrderDoesNotFill(String type, String side, String field, String price) throws Exception {
        awaitPrice("AAPL", "190.40");
        String id = "ord-nofill-" + type + side + price.replace(".", "");
        Consumer<String, String> fills = newConsumer();
        fills.subscribe(List.of("fills"));
        produceOrder(id, "{\"id\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"" + side + "\",\"quantity\":2,"
                + "\"type\":\"" + type + "\",\"" + field + "\":" + price + ",\"status\":\"SUBMITTED\"}");

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(fills, Duration.ofSeconds(5));
        fills.close();
        String joined = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .collect(Collectors.joining("|"));
        assertThat(joined).doesNotContain(id);
    }

    // A would-be-marketable LIMIT on a symbol with no cached price must not fill (and must not throw):
    // the no-price guard runs before the marketability check (no NPE on a null price).
    @Test
    void marketableLimitForUncachedSymbolEmitsNoFill() throws Exception {
        String id = "ord-limit-nocache";
        Consumer<String, String> fills = newConsumer();
        fills.subscribe(List.of("fills"));
        produceOrder(id, "{\"id\":\"" + id + "\",\"symbol\":\"ZZZZ\",\"side\":\"BUY\",\"quantity\":2,"
                + "\"type\":\"LIMIT\",\"limitPrice\":1000000,\"status\":\"SUBMITTED\"}");

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(fills, Duration.ofSeconds(5));
        fills.close();
        String joined = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .collect(Collectors.joining("|"));
        assertThat(joined).doesNotContain(id);
    }

    // A non-marketable order emits an orders.resting event (so order-service can mark it PENDING).
    @Test
    void nonMarketableOrderEmitsARestingEvent() throws Exception {
        awaitPrice("AAPL", "190.40");
        String id = "ord-resting";
        Consumer<String, String> resting = newConsumer();
        resting.subscribe(List.of("orders.resting"));
        produceOrder(id, "{\"id\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":2,"
                + "\"type\":\"LIMIT\",\"limitPrice\":180,\"status\":\"SUBMITTED\"}");

        List<String> all = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            resting.poll(Duration.ofMillis(500)).forEach(record -> all.add(record.value()));
            assertThat(all).anyMatch(value -> value.contains(id));
        });
        resting.close();
    }

    @Test
    void unknownSymbolPriceReturns404() throws Exception {
        mockMvc.perform(get("/prices/NOPE").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    private static void produceOrder(String key, String json) {
        Producer<String, String> producer = newProducer();
        producer.send(new ProducerRecord<>("orders.submitted", key, json));
        producer.flush();
        producer.close();
    }

    @Test
    void orderForAnUncachedSymbolEmitsNoFill() throws Exception {
        Consumer<String, String> fills = newConsumer();
        fills.subscribe(List.of("fills"));
        Producer<String, String> producer = newProducer();
        producer.send(new ProducerRecord<>("orders.submitted", "ZZZZ",
                "{\"id\":\"ord-zzz\",\"symbol\":\"ZZZZ\",\"side\":\"BUY\",\"quantity\":5,\"status\":\"SUBMITTED\"}"));
        producer.flush();
        producer.close();

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(fills, Duration.ofSeconds(5));
        fills.close();

        String joined = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .collect(Collectors.joining("|"));
        assertThat(joined).doesNotContain("ZZZZ");
    }

    private void awaitPrice(String symbol, String price) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String body = mockMvc.perform(get("/prices/" + symbol).header("Authorization", "Bearer " + TOKEN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains(price);
        });
    }

    private static Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }

    private static Consumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "md-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
