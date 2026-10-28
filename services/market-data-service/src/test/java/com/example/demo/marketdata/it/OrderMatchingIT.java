package com.example.demo.marketdata.it;

import static com.example.demo.marketdata.it.harness.MarketDataFixtures.BUY;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.FEED_SYMBOL;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.ORDERS_SUBMITTED;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.ORDER_ID_FIELD;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.SYMBOL_FIELD;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.UNCACHED_SYMBOL;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.restingEvent;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.submittedOrder;
import static com.example.demo.marketdata.it.harness.MarketDataFixtures.submittedTypedOrder;
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

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final String FULL_FILL_ORDER_ID = "ord-full";
    private static final String FULL_FILL_QUANTITY = "7";
    /** Quantity of every typed (LIMIT / STOP) order below. */
    private static final String TYPED_ORDER_QUANTITY = "2";
    private static final String RESTING_ORDER_ID = "ord-resting";
    private static final String LIMIT = "LIMIT";
    private static final String LIMIT_PRICE_FIELD = "limitPrice";
    /** A BUY limit below the market (190.40), so it is not marketable. */
    private static final String LIMIT_BELOW_MARKET = "180";

    @Test
    void orderForAKnownSymbolEmitsExactlyOneFullFill() {
        awaitCachedPrice(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, FULL_FILL_ORDER_ID,
                submittedOrder(FULL_FILL_ORDER_ID, FEED_SYMBOL, BUY, FULL_FILL_QUANTITY));

        List<String> fills = kafka().awaitValuesWhere(OrderMatcher.FILLS_TOPIC, ORDER_ID_FIELD, FULL_FILL_ORDER_ID, 1, WAIT);
        assertThat(fills).hasSize(1);
        FillEvent fill = Json.read(fills.getFirst(), FillEvent.class);
        assertSoftly(softly -> {
            softly.assertThat(fill.orderId()).isEqualTo(FULL_FILL_ORDER_ID);
            softly.assertThat(fill.symbol()).isEqualTo(FEED_SYMBOL);
            softly.assertThat(fill.side()).isEqualTo(BUY);
            softly.assertThat(fill.quantity()).isEqualByComparingTo(FULL_FILL_QUANTITY);
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
        awaitCachedPrice(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, id,
                submittedTypedOrder(id, FEED_SYMBOL, side, TYPED_ORDER_QUANTITY, type, field, price));

        List<String> fills = kafka().awaitValuesWhere(OrderMatcher.FILLS_TOPIC, ORDER_ID_FIELD, id, 1, WAIT);
        assertThat(fills).hasSize(1);
        FillEvent fill = Json.read(fills.getFirst(), FillEvent.class);
        assertSoftly(softly -> {
            softly.assertThat(fill.side()).isEqualTo(side);
            softly.assertThat(fill.quantity()).isEqualByComparingTo(TYPED_ORDER_QUANTITY);
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
        awaitCachedPrice(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, id,
                submittedTypedOrder(id, FEED_SYMBOL, side, TYPED_ORDER_QUANTITY, type, field, price));

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, ORDER_ID_FIELD, id, 1, WAIT))
                .containsExactly(restingEvent(id));
        assertThat(kafka().currentValuesWhere(OrderMatcher.FILLS_TOPIC, ORDER_ID_FIELD, id)).isEmpty();
    }

    // A non-marketable order emits an orders.resting event (so order-service can mark it PENDING).
    @Test
    void nonMarketableOrderEmitsARestingEvent() {
        awaitCachedPrice(FEED_SYMBOL, ExchangeFeedStubs.AAPL_LAST_PRICE);

        kafka().send(ORDERS_SUBMITTED, RESTING_ORDER_ID, submittedTypedOrder(RESTING_ORDER_ID, FEED_SYMBOL, BUY,
                TYPED_ORDER_QUANTITY, LIMIT, LIMIT_PRICE_FIELD, LIMIT_BELOW_MARKET));

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, ORDER_ID_FIELD, RESTING_ORDER_ID, 1, WAIT))
                .containsExactly(restingEvent(RESTING_ORDER_ID));
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

        assertThat(kafka().awaitValuesWhere(OrderMatcher.RESTING_TOPIC, ORDER_ID_FIELD, id, 1, WAIT))
                .containsExactly(restingEvent(id));
        assertThat(kafka().currentValuesWhere(OrderMatcher.FILLS_TOPIC, SYMBOL_FIELD, UNCACHED_SYMBOL)).isEmpty();
    }
}
