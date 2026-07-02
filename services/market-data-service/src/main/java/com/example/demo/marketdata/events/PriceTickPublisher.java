package com.example.demo.marketdata.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.demo.marketdata.model.PriceTick;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Publishes {@code price.ticks} events to Kafka (keyed by symbol). */
@Component
public class PriceTickPublisher {

    public static final String TOPIC = "price.ticks";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PriceTickPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PriceTick tick) {
        kafkaTemplate.send(TOPIC, tick.symbol(), MAPPER.writeValueAsString(tick));
    }
}
