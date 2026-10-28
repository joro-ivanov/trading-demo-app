package com.example.demo.e2e.framework.support;

import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderFactory {
    /**
     * Headroom above the latest market price that keeps a BUY limit or a SELL stop marketable while the
     * demo feed drifts (at most 0.50 per second), yet keeps the notional realistic for the pre-trade risk
     * check, which values a LIMIT at its limit and a STOP at its stop.
     */
    private static final BigDecimal MARKETABLE_HEADROOM = new BigDecimal("1.10");

    private OrderFactory() {
    }

    public static OrderRequest limitBuy(String symbol, int quantity, BigDecimal price) {
        return OrderRequestBuilder.anOrder()
                .symbol(symbol)
                .side(Side.BUY)
                .quantity(quantity)
                .type(OrderType.LIMIT)
                .limitPrice(price)
                .build();
    }

    public static OrderRequest limitSell(String symbol, int quantity, BigDecimal price) {
        return OrderRequestBuilder.anOrder()
                .symbol(symbol)
                .side(Side.SELL)
                .quantity(quantity)
                .type(OrderType.LIMIT)
                .limitPrice(price)
                .build();
    }

    public static OrderRequest stopBuy(String symbol, int quantity, BigDecimal price) {
        return OrderRequestBuilder.anOrder()
                .symbol(symbol)
                .side(Side.BUY)
                .quantity(quantity)
                .type(OrderType.STOP)
                .stopPrice(price)
                .build();
    }

    public static OrderRequest stopSell(String symbol, int quantity, BigDecimal price) {
        return OrderRequestBuilder.anOrder()
                .symbol(symbol)
                .side(Side.SELL)
                .quantity(quantity)
                .type(OrderType.STOP)
                .stopPrice(price)
                .build();
    }

    /** A BUY limit 10% above {@code marketPrice}, rounded up to cents: immediately marketable. */
    public static BigDecimal marketableBuyLimit(BigDecimal marketPrice) {
        return tenPercentAbove(marketPrice);
    }

    /** A SELL stop 10% above {@code marketPrice}, rounded up to cents: already breached, so it fills. */
    public static BigDecimal marketableSellStop(BigDecimal marketPrice) {
        return tenPercentAbove(marketPrice);
    }

    private static BigDecimal tenPercentAbove(BigDecimal marketPrice) {
        return marketPrice.multiply(MARKETABLE_HEADROOM).setScale(2, RoundingMode.UP);
    }
}
