package com.example.demo.marketdata.match;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.demo.common.money.Money;
import com.example.demo.marketdata.cache.PriceCache;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal matching engine: consumes {@code orders.submitted} and fills each order at the latest
 * price cached from the exchange feed, emitting a {@code fills} event. This is the link that lets
 * the cross-service flow (order → fill → portfolio) complete (docs/DESIGN.md §3).
 */
@Component
public class OrderMatcher {

    public static final String FILLS_TOPIC = "fills";
    public static final String RESTING_TOPIC = "orders.resting";

    private static final Logger LOG = LoggerFactory.getLogger(OrderMatcher.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final AtomicLong FILL_SEQUENCE = new AtomicLong();

    private final PriceCache priceCache;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OrderMatcher(PriceCache priceCache, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.priceCache = priceCache;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @KafkaListener(topics = "orders.submitted", groupId = "market-data-matcher")
    public void onOrderSubmitted(String payload) {
        OrderSubmittedEvent order = MAPPER.readValue(payload, OrderSubmittedEvent.class);
        Optional<Money> price = priceCache.latest(order.symbol());
        if (price.isEmpty()) {
            rest(order, "no cached price");
            return;
        }
        BigDecimal market = price.get().amount();
        if (!isMarketable(order, market)) {
            rest(order, "not marketable at " + market);
            return;
        }
        FillEvent fill = new FillEvent(order.id(), order.symbol(), order.side(), order.quantity(), market,
                order.owner(), clock.instant(), FILL_SEQUENCE.incrementAndGet());
        kafkaTemplate.send(FILLS_TOPIC, order.symbol(), MAPPER.writeValueAsString(fill));
        LOG.info("Filled order {} {} {} @ {}", order.id(), order.side(), order.symbol(), market);
    }

    /** An order that can't execute now rests — emit it so order-service can mark it PENDING. */
    private void rest(OrderSubmittedEvent order, String reason) {
        LOG.info("Order {} ({}) resting: {}", order.id(), order.type(), reason);
        kafkaTemplate.send(RESTING_TOPIC, order.symbol(),
                MAPPER.writeValueAsString(new OrderRestingEvent(order.id())));
    }

    private record OrderRestingEvent(String orderId) {
    }

    private boolean isMarketable(OrderSubmittedEvent order, BigDecimal market) {
        return MatchingRules.isMarketable(order.type(), order.side(), market,
                order.limitPrice(), order.stopPrice());
    }
}
