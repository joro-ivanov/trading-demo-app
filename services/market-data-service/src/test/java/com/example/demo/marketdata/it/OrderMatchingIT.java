package com.example.demo.marketdata.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.demo.common.json.Json;
import com.example.demo.marketdata.it.harness.MarketDataITBase;
import com.example.demo.marketdata.it.harness.stubs.ExchangeFeedStubs;
import com.example.demo.marketdata.match.FillEvent;
import com.example.demo.marketdata.match.OrderMatcher;

/**
 * {@code orders.submitted} listener ({@link OrderMatcher}): fills a marketable order at the cached
 * market price ({@code fills}), otherwise rests it ({@code orders.resting}). Fill and rest are
 * mutually exclusive, so a resting event is the deterministic proof that no fill will follow.
 * Every order uses its own id.
 */
class OrderMatchingIT extends MarketDataITBase {

    private static final String ORDERS_SUBMITTED = "orders.submitted";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void orderForAKnownSymbolEmitsExactlyOneFullFill() {
        awaitCachedPrice("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, "ord-full", "{\"id\":\"ord-full\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":7,\"status\":\"SUBMITTED\"}");

        List<String> fills = kafka().awaitValuesWhere(OrderMatcher.FILLS_TOPIC, "orderId", "ord-full", 1, WAIT);
        assertThat(fills).hasSize(1);
        FillEvent fill = Json.read(fills.getFirst(), FillEvent.class);
        assertSoftly(softly -> {
            softly.assertThat(fill.orderId()).isEqualTo("ord-full");
            softly.assertThat(fill.symbol()).isEqualTo("AAPL");
            softly.assertThat(fill.side()).isEqualTo("BUY");
            softly.assertThat(fill.quantity()).isEqualByComparingTo("7");
            softly.assertThat(fill.price()).isEqualByComparingTo(ExchangeFeedStubs.AAPL_LAST_PRICE);
            softly.assertThat(fill.filledAt()).isEqualTo(NOW);
        });
    }

    // All four directions, market = 190.40: BUY LIMIT fills if market ≤ limit; SELL LIMIT if market ≥
    // limit; BUY STOP if market ≥ stop; SELL STOP if market ≤ stop. The "==190.40" rows pin the
    // inclusive boundary (≤/≥, not </>). Every fill must be at the MARKET price (so a LIMIT gets
    // price improvement — it never fills at its stated limit).
    @ParameterizedTest(name = "{1} {2} {3}={4} fills at market 190.40")
    @CsvSource({
        "ord-fill-LIMITBUY19040,LIMIT,BUY,limitPrice,190.40", "ord-fill-LIMITBUY200,LIMIT,BUY,limitPrice,200",
        "ord-fill-LIMITSELL19040,LIMIT,SELL,limitPrice,190.40", "ord-fill-LIMITSELL180,LIMIT,SELL,limitPrice,180",
        "ord-fill-STOPBUY19040,STOP,BUY,stopPrice,190.40", "ord-fill-STOPBUY180,STOP,BUY,stopPrice,180",
        "ord-fill-STOPSELL19040,STOP,SELL,stopPrice,190.40", "ord-fill-STOPSELL200,STOP,SELL,stopPrice,200"
    })
    void marketableTypedOrderFillsAtMarketPrice(String id, String type, String side, String field, String price) {
        awaitCachedPrice("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, id, "{\"id\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"" + side
                + "\",\"quantity\":2,\"type\":\"" + type + "\",\"" + field + "\":" + price + ",\"status\":\"SUBMITTED\"}");

        List<String> fills = kafka().awaitValuesWhere(OrderMatcher.FILLS_TOPIC, "orderId", id, 1, WAIT);
        assertThat(fills).hasSize(1);
        FillEvent fill = Json.read(fills.getFirst(), FillEvent.class);
        assertSoftly(softly -> {
            softly.assertThat(fill.side()).isEqualTo(side);
            softly.assertThat(fill.quantity()).isEqualByComparingTo("2");
            softly.assertThat(fill.price()).isEqualByComparingTo(ExchangeFeedStubs.AAPL_LAST_PRICE);
        });
    }

    // The mirror: a non-marketable LIMIT/STOP on each side rests and emits NO fill. The SELL STOP row
    // is the "stop that never triggers" case (a stop-loss that didn't fire).
    @ParameterizedTest(name = "{1} {2} {3}={4} rests at 190.40")
    @CsvSource({
        "ord-nofill-LIMITBUY180,LIMIT,BUY,limitPrice,180", "ord-nofill-LIMITSELL200,LIMIT,SELL,limitPrice,200",
        "ord-nofill-STOPBUY200,STOP,BUY,stopPrice,200", "ord-nofill-STOPSELL180,STOP,SELL,stopPrice,180"
    })
    void nonMarketableTypedOrderRestsWithoutAFill(String id, String type, String side, String field, String price) {
        awaitCachedPrice("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, id, "{\"id\":\"" + id + "\",\"symbol\":\"AAPL\",\"side\":\"" + side
                + "\",\"quantity\":2,\"type\":\"" + type + "\",\"" + field + "\":" + price + ",\"status\":\"SUBMITTED\"}");

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, "orderId", id, 1, WAIT))
                .containsExactly("{\"orderId\":\"" + id + "\"}");
        assertThat(kafka().currentValuesWhere(OrderMatcher.FILLS_TOPIC, "orderId", id)).isEmpty();
    }

    // A non-marketable order emits an orders.resting event (so order-service can mark it PENDING).
    @Test
    void nonMarketableOrderEmitsARestingEvent() {
        awaitCachedPrice("AAPL", ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, "ord-resting", "{\"id\":\"ord-resting\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
                + "\"quantity\":2,\"type\":\"LIMIT\",\"limitPrice\":180,\"status\":\"SUBMITTED\"}");

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, "orderId", "ord-resting", 1, WAIT))
                .containsExactly("{\"orderId\":\"ord-resting\"}");
    }

    // A symbol with no cached price never fills, whatever the type: the no-price guard runs before the
    // marketability check (so a would-be-marketable LIMIT doesn't NPE on a null price); the order rests.
    @ParameterizedTest(name = "{0}: uncached symbol rests without a fill")
    @CsvSource(delimiter = '|', value = {
        "ord-zzz|{\"id\":\"ord-zzz\",\"symbol\":\"ZZZZ\",\"side\":\"BUY\",\"quantity\":5,\"status\":\"SUBMITTED\"}",
        "ord-limit-nocache|{\"id\":\"ord-limit-nocache\",\"symbol\":\"ZZZZ\",\"side\":\"BUY\",\"quantity\":2,"
                + "\"type\":\"LIMIT\",\"limitPrice\":1000000,\"status\":\"SUBMITTED\"}"
    })
    void orderForAnUncachedSymbolRestsWithoutAFill(String id, String order) {
        kafka().send(ORDERS_SUBMITTED, id, order);

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, "orderId", id, 1, WAIT))
                .containsExactly("{\"orderId\":\"" + id + "\"}");
        assertThat(kafka().currentValuesWhere(OrderMatcher.FILLS_TOPIC, "symbol", "ZZZZ")).isEmpty();
    }
}
