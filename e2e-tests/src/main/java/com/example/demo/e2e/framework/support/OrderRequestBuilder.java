package com.example.demo.e2e.framework.support;

import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderStatus;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;

import java.math.BigDecimal;

public class OrderRequestBuilder {
    private String id;
    private String symbol = TradingSymbols.GOOG;
    private Side side = Side.BUY;
    private BigDecimal quantity = BigDecimal.ONE;
    private OrderType type = OrderType.MARKET;
    private BigDecimal limitPrice = new BigDecimal("0.01");
    private BigDecimal stopPrice;
    private OrderStatus status;
    private String owner = null;
    private String username = null;

    private OrderRequestBuilder() {
    }

    public static OrderRequestBuilder anOrder() {
        return new OrderRequestBuilder();
    }

    public OrderRequestBuilder symbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public OrderRequestBuilder side(Side side) {
        this.side = side;
        return this;
    }

    public OrderRequestBuilder quantity(int quantity) {
        this.quantity = BigDecimal.valueOf(quantity);
        return this;
    }

    public OrderRequestBuilder quantity(BigDecimal quantity) {
        this.quantity = quantity;
        return this;
    }

    public OrderRequestBuilder type(OrderType type) {
        this.type = type;
        return this;
    }

    public OrderRequestBuilder limitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
        return this;
    }

    public OrderRequestBuilder stopPrice(BigDecimal stopPrice) {
        this.stopPrice = stopPrice;
        return this;
    }

    public OrderRequestBuilder owner(String owner) {
        this.owner = owner;
        return this;
    }

    public OrderRequestBuilder username(String username) {
        this.username = username;
        return this;
    }

    public OrderRequest build() {
        return new OrderRequest(
                symbol,
                side,
                quantity,
                type,
                limitPrice,
                stopPrice,
                owner,
                username
        );
    }
}
