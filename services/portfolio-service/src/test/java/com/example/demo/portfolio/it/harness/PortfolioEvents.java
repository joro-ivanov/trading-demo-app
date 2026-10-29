package com.example.demo.portfolio.it.harness;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.demo.portfolio.events.FillEvent;
import com.example.demo.portfolio.events.PriceMessage;
import com.example.demo.testing.support.fixtures.Topics;
import com.example.demo.testing.support.kafka.KafkaProbe;

/**
 * Produces the inbound Kafka traffic portfolio-service consumes: fills (from market-data's matcher) and
 * price ticks, as the consumer's own event records, through the shared {@link KafkaProbe} (every send is
 * acknowledged before returning, so messages keyed by the same symbol arrive in the order the test sent them).
 */
public final class PortfolioEvents {

    /** Default {@code filledAt} for fills whose timestamp is not under test. */
    public static final String DEFAULT_FILLED_AT = "2026-07-01T10:00:00Z";

    private final KafkaProbe kafka;

    PortfolioEvents(KafkaProbe kafka) {
        this.kafka = kafka;
    }

    /**
     * A {@link FillEvent} on the {@code fills} topic (the shape market-data's {@code FillEvent} produces).
     * The order id is derived as {@code o-<symbol>-<owner>-<side>} so tests can assert it exactly.
     */
    public void sendFill(String owner, String symbol, String side, String quantity, String price,
                         String filledAt, long sequenceId) {
        kafka.send(Topics.FILLS, symbol, new FillEvent(orderId(symbol, owner, side), symbol, side,
                new BigDecimal(quantity), new BigDecimal(price), owner, Instant.parse(filledAt), sequenceId));
    }

    /** A fill with the default timestamp and sequence id 1. */
    public void sendFill(String owner, String symbol, String side, String quantity, String price) {
        sendFill(owner, symbol, side, quantity, price, DEFAULT_FILLED_AT, 1L);
    }

    /** A {@link PriceMessage} on the {@code price.ticks} topic (the shape market-data's {@code PriceTick} produces). */
    public void sendTick(String symbol, String price) {
        kafka.send(Topics.PRICE_TICKS, symbol, new PriceMessage(symbol, new BigDecimal(price)));
    }

    /** The order id {@link #sendFill} assigns. */
    public static String orderId(String symbol, String owner, String side) {
        return "o-" + symbol + "-" + owner + "-" + side;
    }
}
