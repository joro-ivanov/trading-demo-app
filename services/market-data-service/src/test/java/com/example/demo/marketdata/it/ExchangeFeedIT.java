package com.example.demo.marketdata.it;

import static com.example.demo.marketdata.it.harness.MarketDataFixtures.FEED_SYMBOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.marketdata.events.PriceTickPublisher;
import com.example.demo.marketdata.it.harness.MarketDataITBase;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.model.PriceTick;

/**
 * Exchange WS feed → {@code price.ticks}: the service subscribes to the WireMock feed on startup and
 * republishes every frame, in order, to Kafka.
 */
class ExchangeFeedIT extends MarketDataITBase {

    private static final Duration WAIT = Duration.ofSeconds(20);
    /** The feed stub sends a burst of two AAPL frames on subscribe. */
    private static final int FEED_FRAMES = 2;

    @Test
    void republishesEveryFeedFrameToPriceTicksInOrder() {
        List<PriceTick> ticks = kafka().awaitRecordsWhere(PriceTickPublisher.TOPIC, PriceTick.class, PriceTick::symbol,
                FEED_SYMBOL, FEED_FRAMES, WAIT);

        assertThat(ticks)
                .extracting(PriceTick::symbol, tick -> tick.price().toPlainString())
                .containsExactly(
                        tuple(FEED_SYMBOL, ExchangeFeedStubs.AAPL_FIRST_PRICE),
                        tuple(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE));
    }
}
