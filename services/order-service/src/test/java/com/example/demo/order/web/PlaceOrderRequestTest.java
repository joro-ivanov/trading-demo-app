package com.example.demo.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.demo.order.domain.OrderType;
import com.example.demo.order.domain.Side;

/** Pure unit coverage of the request's defaulting + cross-field rules (no Spring context). */
class PlaceOrderRequestTest {

    private static final String SYMBOL = "AAPL";

    private static PlaceOrderRequest req(OrderType type, BigDecimal limit, BigDecimal stop) {
        return new PlaceOrderRequest(SYMBOL, Side.BUY, BigDecimal.ONE, type, limit, stop);
    }

    @Test
    void typeDefaultsToMarketWhenNull() {
        assertThat(req(null, null, null).typeOrDefault()).isEqualTo(OrderType.MARKET);
    }

    @Test
    void typeIsKeptWhenPresent() {
        assertThat(req(OrderType.STOP, null, BigDecimal.TEN).typeOrDefault()).isEqualTo(OrderType.STOP);
    }

    @Test
    void limitOrderWithoutLimitPriceIsInvalid() {
        assertThat(req(OrderType.LIMIT, null, null).isLimitPricePresentWhenRequired()).isFalse();
    }

    @Test
    void limitOrderWithLimitPriceIsValid() {
        assertThat(req(OrderType.LIMIT, BigDecimal.TEN, null).isLimitPricePresentWhenRequired()).isTrue();
    }

    @Test
    void stopOrderWithoutStopPriceIsInvalid() {
        assertThat(req(OrderType.STOP, null, null).isStopPricePresentWhenRequired()).isFalse();
    }

    @Test
    void stopOrderWithStopPriceIsValid() {
        assertThat(req(OrderType.STOP, null, BigDecimal.TEN).isStopPricePresentWhenRequired()).isTrue();
    }

    @Test
    void marketOrderNeedsNeitherPrice() {
        PlaceOrderRequest market = req(OrderType.MARKET, null, null);
        assertSoftly(softly -> {
            softly.assertThat(market.isLimitPricePresentWhenRequired()).isTrue();
            softly.assertThat(market.isStopPricePresentWhenRequired()).isTrue();
        });
    }
}
