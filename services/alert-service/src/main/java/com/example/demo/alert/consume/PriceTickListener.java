package com.example.demo.alert.consume;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.demo.alert.service.AlertTriggering;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Feeds {@code price.ticks} into alert triggering. A malformed tick is dropped, never fatal. */
@Component
public class PriceTickListener {

    private static final Logger LOG = LoggerFactory.getLogger(PriceTickListener.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final AlertTriggering triggering;

    public PriceTickListener(AlertTriggering triggering) {
        this.triggering = triggering;
    }

    @KafkaListener(topics = "price.ticks", groupId = "alert-service-ticks")
    public void onPriceTick(String payload) {
        PriceTick tick;
        try {
            tick = MAPPER.readValue(payload, PriceTick.class);
        } catch (JacksonException exception) {
            LOG.warn("Dropping malformed price tick payload");
            return;
        }
        if (tick.symbol() == null || tick.price() == null) {
            LOG.warn("Dropping price tick without symbol or price");
            return;
        }
        triggering.onPrice(tick.symbol(), tick.price());
    }

    private record PriceTick(String symbol, BigDecimal price) {
    }
}
