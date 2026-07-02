package com.example.demo.marketdata.cache;

import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.demo.common.money.Money;
import com.example.demo.marketdata.model.PriceTick;

/** Caches the latest price per symbol in Redis as fixed-point {@link Money}. */
@Component
public class PriceCache {

    private static final String KEY_PREFIX = "price:";

    private final StringRedisTemplate redis;

    public PriceCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void put(PriceTick tick) {
        redis.opsForValue().set(KEY_PREFIX + tick.symbol(), Money.of(tick.price()).amount().toPlainString());
    }

    public Optional<Money> latest(String symbol) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + symbol)).map(Money::of);
    }
}
