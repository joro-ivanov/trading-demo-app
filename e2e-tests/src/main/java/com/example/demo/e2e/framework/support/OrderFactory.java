package com.example.demo.e2e.framework.support;

import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;

import java.math.BigDecimal;

public class OrderFactory {
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
}
