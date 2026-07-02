package com.example.demo.marketdata.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.marketdata.model.PriceTick;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Synthetic price feed used by docker-compose ({@code market-data.feed.mode=demo}), so the stack
 * is live without a real (or mocked) exchange. Random-walks a price per configured symbol and
 * feeds it through the same {@link PriceProcessor} the real feed uses.
 */
@Component
@ConditionalOnProperty(name = "market-data.feed.mode", havingValue = "demo")
public class DemoFeed {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final PriceProcessor processor;
    private final List<String> symbols;
    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public DemoFeed(PriceProcessor processor, @Value("${market-data.symbols}") List<String> symbols) {
        this.processor = processor;
        this.symbols = symbols;
        symbols.forEach(symbol -> prices.put(symbol, BigDecimal.valueOf(100)));
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        symbols.forEach(this::emit);
    }

    private void emit(String symbol) {
        BigDecimal current = prices.get(symbol);
        BigDecimal delta = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(-0.5, 0.5));
        BigDecimal next = current.add(delta).max(BigDecimal.ONE).setScale(2, RoundingMode.HALF_EVEN);
        prices.put(symbol, next);
        processor.process(MAPPER.writeValueAsString(new PriceTick(symbol, next)));
    }
}
