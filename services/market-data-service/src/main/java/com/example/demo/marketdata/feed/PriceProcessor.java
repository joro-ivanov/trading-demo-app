package com.example.demo.marketdata.feed;

import org.springframework.stereotype.Component;

import com.example.demo.marketdata.cache.PriceCache;
import com.example.demo.marketdata.events.PriceTickPublisher;
import com.example.demo.marketdata.model.PriceTick;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Parses an inbound exchange feed frame, caches the latest price, and republishes it to Kafka. */
@Component
public class PriceProcessor {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final PriceCache cache;
    private final PriceTickPublisher publisher;

    public PriceProcessor(PriceCache cache, PriceTickPublisher publisher) {
        this.cache = cache;
        this.publisher = publisher;
    }

    public void process(String frame) {
        PriceTick tick = MAPPER.readValue(frame, PriceTick.class);
        cache.put(tick);
        publisher.publish(tick);
    }
}
