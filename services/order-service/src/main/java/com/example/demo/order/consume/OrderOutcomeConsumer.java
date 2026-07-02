package com.example.demo.order.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.order.domain.OrderRepository;
import com.example.demo.order.domain.OrderStatus;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves an accepted (SUBMITTED) order to its terminal trading state from the matcher's outcome events:
 * a {@code fills} message marks it FILLED; an {@code orders.resting} message (the matcher found it
 * non-marketable, e.g. a limit/stop that can't execute now) marks it PENDING. The matcher emits
 * exactly one of the two per order, so the transitions don't race. Guards keep them idempotent and
 * never override a CANCELLED order.
 */
@Component
public class OrderOutcomeConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderOutcomeConsumer.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final OrderRepository repository;

    public OrderOutcomeConsumer(OrderRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "fills", groupId = "order-service-fills")
    @Transactional
    public void onFill(String payload) {
        FillEvent fill = MAPPER.readValue(payload, FillEvent.class);
        repository.findById(UUID.fromString(fill.orderId())).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.FILLED);
                repository.save(order);
                LOG.info("Order {} -> FILLED", order.getId());
            }
        });
    }

    @KafkaListener(topics = "orders.resting", groupId = "order-service-resting")
    @Transactional
    public void onResting(String payload) {
        OrderRestingEvent event = MAPPER.readValue(payload, OrderRestingEvent.class);
        repository.findById(UUID.fromString(event.orderId())).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.SUBMITTED) {
                order.setStatus(OrderStatus.PENDING);
                repository.save(order);
                LOG.info("Order {} -> PENDING", order.getId());
            }
        });
    }

    // owner is carried on the fill (for portfolio); not needed here, but declared so the local
    // mapper accepts the field.
    private record FillEvent(String orderId, String symbol, String side, BigDecimal quantity, BigDecimal price,
                             String owner, Instant filledAt, Long sequenceId) {
    }

    private record OrderRestingEvent(String orderId) {
    }
}
