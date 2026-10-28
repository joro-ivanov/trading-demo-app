package com.example.demo.marketdata.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.marketdata.events.PriceTickPublisher;
import com.example.demo.marketdata.it.harness.MarketDataITBase;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.model.PriceTick;

/**
 * Exchange WS feed → {@code price.ticks}: the service subscribes to the WireMock feed on startup and
 * republishes every frame, in order, to Kafka.
 */
class ExchangeFeedIT extends MarketDataITBase {

    @Test
    void republishesEveryFeedFrameToPriceTicksInOrder() {
        List<String> values = kafka().awaitValuesWhere(PriceTickPublisher.TOPIC, "symbol", "AAPL", 2,
                Duration.ofSeconds(20));

        List<PriceTick> ticks = values.stream().map(value -> Json.read(value, PriceTick.class)).toList();
        assertThat(ticks)
                .extracting(PriceTick::symbol, tick -> tick.price().toPlainString())
                .containsExactly(
                        tuple("AAPL", ExchangeFeedStubs.AAPL_FIRST_PRICE),
                        tuple("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE));
    }
}
