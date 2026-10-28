package com.example.demo.portfolio.it.harness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.events.FillEvent;
import com.example.demo.portfolio.events.PriceMessage;

/**
 * Produces the inbound Kafka traffic portfolio-service consumes: fills (from market-data's matcher)
 * and price ticks, as the consumer's own event records serialized with {@link Json}. Every send is
 * flushed before returning, so messages keyed by the same symbol arrive in the order the test sent them.
 */
public final class KafkaProbe {

    /** Default {@code filledAt} for fills whose timestamp is not under test. */
    public static final String DEFAULT_FILLED_AT = "2026-07-01T10:00:00Z";

    private final Producer<String, String> producer;

    KafkaProbe(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        this.producer = new KafkaProducer<>(props);
    }

    /**
     * A {@link FillEvent} on the {@code fills} topic (the shape market-data's {@code FillEvent} produces).
     * The order id is derived as {@code o-<symbol>-<owner>-<side>} so tests can assert it exactly.
     */
    public void sendFill(String owner, String symbol, String side, String quantity, String price,
                         String filledAt, long sequenceId) {
        send("fills", symbol, new FillEvent(orderId(symbol, owner, side), symbol, side, new BigDecimal(quantity),
                new BigDecimal(price), owner, Instant.parse(filledAt), sequenceId));
    }

    /** A fill with the default timestamp and sequence id 1. */
    public void sendFill(String owner, String symbol, String side, String quantity, String price) {
        sendFill(owner, symbol, side, quantity, price, DEFAULT_FILLED_AT, 1L);
    }

    /** A {@link PriceMessage} on the {@code price.ticks} topic (the shape market-data's {@code PriceTick} produces). */
    public void sendTick(String symbol, String price) {
        send("price.ticks", symbol, new PriceMessage(symbol, new BigDecimal(price)));
    }

    /** The order id {@link #sendFill} assigns. */
    public static String orderId(String symbol, String owner, String side) {
        return "o-" + symbol + "-" + owner + "-" + side;
    }

    /** Sends {@code event} serialized with the project's {@link Json}. */
    public void send(String topic, String key, Object event) {
        producer.send(new ProducerRecord<>(topic, key, Json.write(event)));
        producer.flush();
    }
}
