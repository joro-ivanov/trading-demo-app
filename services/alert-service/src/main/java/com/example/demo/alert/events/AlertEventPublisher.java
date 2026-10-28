package com.example.demo.alert.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.demo.alert.domain.AlertEntity;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes {@code alerts.triggered}, keyed by owner (per-user ordering). Sent only after the
 * TRIGGERED state commits, so a rolled-back trigger is never announced.
 */
@Component
public class AlertEventPublisher {

    public static final String TOPIC = "alerts.triggered";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AlertEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Must be called inside the transaction that triggered {@code alert}. */
    public void publishTriggered(AlertEntity alert) {
        String key = alert.getOwner();
        String payload = MAPPER.writeValueAsString(AlertTriggeredEvent.from(alert));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(TOPIC, key, payload);
            }
        });
    }
}
