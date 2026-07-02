package com.example.demo.order.events;

import java.math.BigDecimal;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.demo.order.domain.OrderEntity;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes {@code orders.submitted} events to Kafka. Serializes to a JSON string with a local
 * Jackson 3 mapper and uses a {@code <String, String>} template (String serializers configured
 * in application.yaml) to avoid serializer-config ambiguity.
 */
@Component
public class OrderEventPublisher {

    public static final String TOPIC = "orders.submitted";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishSubmitted(OrderEntity order) {
        String payload = MAPPER.writeValueAsString(new OrderSubmittedEvent(
                order.getId().toString(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getType().name(),
                order.getLimitPrice(),
                order.getStopPrice(),
                order.getStatus().name(),
                order.getOwner()));
        // Publish after commit so downstream fill consumers can never observe a fill before the
        // SUBMITTED order row is visible in order-service's database.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(TOPIC, order.getId().toString(), payload);
            }
        });
    }

    // owner: the order's owner, carried downstream so portfolio can build a per-user position.
    private record OrderSubmittedEvent(String id, String symbol, String side, BigDecimal quantity,
                                       String type, BigDecimal limitPrice, BigDecimal stopPrice, String status,
                                       String owner) {
    }
}
