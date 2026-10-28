package com.example.demo.order.it.harness;

import java.math.BigDecimal;

import com.example.demo.order.domain.OrderType;

/**
 * {@code POST /orders} bodies built from named values. Numbers are passed as text and kept as
 * {@link BigDecimal}, so a test controls the exact representation (scale) that goes over the wire.
 */
public final class Orders {

    private Orders() {
    }

    /** A MARKET order with no {@code type} (the service defaults it). */
    public static OrderRequest market(String symbol, String side, String quantity) {
        return new OrderRequest(symbol, side, new BigDecimal(quantity), null, null, null);
    }

    public static OrderRequest limit(String symbol, String side, String quantity, String limitPrice) {
        return new OrderRequest(symbol, side, new BigDecimal(quantity), OrderType.LIMIT.name(),
                new BigDecimal(limitPrice), null);
    }

    /** A MARKET order that also claims an {@code owner} in the body (which the service must ignore). */
    public static OwnerClaimingOrderRequest claimingOwner(String symbol, String side, String quantity, String owner) {
        return new OwnerClaimingOrderRequest(symbol, side, new BigDecimal(quantity), owner);
    }
}
