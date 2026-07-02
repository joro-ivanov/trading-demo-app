package com.example.demo.notification.consume;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bridges Kafka to the browser: each {@code price.ticks} message is fanned out to
 * {@code /topic/prices.<symbol>} and each {@code fills} message is delivered only to its owner via
 * {@code /user/queue/fills}.
 */
@Component
public class NotificationListeners {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListeners.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final SimpMessagingTemplate messaging;

    public NotificationListeners(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @KafkaListener(topics = "price.ticks", groupId = "notification-prices")
    public void onPriceTick(String payload) {
        try {
            String symbol = MAPPER.readValue(payload, SymbolHolder.class).symbol();
            messaging.convertAndSend("/topic/prices." + symbol, payload);
        } catch (JacksonException exception) {
            LOG.warn("Dropping malformed price tick payload");
        }
    }

    @KafkaListener(topics = "fills", groupId = "notification-fills")
    public void onFill(String payload) {
        try {
            FillEvent fill = MAPPER.readValue(payload, FillEvent.class);
            messaging.convertAndSendToUser(fill.owner(), "/queue/fills", payload);
        } catch (JacksonException exception) {
            LOG.warn("Dropping malformed fill payload");
        }
    }

    private record SymbolHolder(String symbol) {
    }

    private record FillEvent(String owner) {
    }
}
