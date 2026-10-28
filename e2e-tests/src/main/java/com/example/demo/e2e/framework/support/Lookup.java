package com.example.demo.e2e.framework.support;

import java.math.BigDecimal;
import java.util.List;

import com.example.demo.e2e.framework.model.Fill;
import com.example.demo.e2e.framework.model.OrderResponse;
import com.example.demo.e2e.framework.model.Position;

/**
 * Finds one element of a live API list by its natural key, so tests stay linear (no stream filters).
 * A missing element fails with an {@link AssertionError}, which an Awaitility poll retries.
 */
public final class Lookup {

    private Lookup() {
    }

    /** The order with {@code orderId} in a user's order history. */
    public static OrderResponse order(List<OrderResponse> orders, String orderId) {
        return orders.stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order for " + orderId));
    }

    /** The position in {@code symbol} in a user's portfolio. */
    public static Position position(List<Position> positions, String symbol) {
        return positions.stream()
                .filter(position -> position.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + symbol));
    }

    /** The quantity held in {@code symbol}; zero when the user has no position in it. */
    public static BigDecimal heldQuantity(List<Position> positions, String symbol) {
        return positions.stream()
                .filter(position -> position.symbol().equals(symbol))
                .map(Position::quantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /** The fill of the order {@code orderId} in a user's recent fills. */
    public static Fill fill(List<Fill> fills, String orderId) {
        return fills.stream()
                .filter(fill -> fill.orderId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No fill for " + orderId));
    }
}
