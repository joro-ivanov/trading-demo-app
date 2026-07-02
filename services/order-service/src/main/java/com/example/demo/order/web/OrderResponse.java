package com.example.demo.order.web;

import java.math.BigDecimal;

import com.example.demo.order.domain.OrderEntity;

/** Order representation returned by the REST API. */
public record OrderResponse(String id, String symbol, String side, BigDecimal quantity, String type,
                            BigDecimal limitPrice, BigDecimal stopPrice, String owner, String status) {

    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId().toString(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getType().name(),
                order.getLimitPrice(),
                order.getStopPrice(),
                order.getOwner(),
                order.getStatus().name());
    }
}
