package com.example.demo.e2e.framework.service.order;

import java.math.BigDecimal;

import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.model.OrderRequest;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.support.OrderRequestBuilder;

/**
 * Order placement/cancellation functionality of the order-service. Dumb pipe, static methods,
 * no validation (docs/DESIGN.md §8.2, §8.4).
 */
public final class OrdersService extends BaseOrderService {

    private OrdersService() {
    }

    public static ApiResponse placeMarketOrder(String token, String symbol, Side side, BigDecimal quantity) {
        OrderRequest order = OrderRequestBuilder.anOrder().type(OrderType.MARKET).symbol(symbol).side(side).quantity(quantity).build();
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(order)
                .build());
    }

    public static ApiResponse placeLimitOrder(String token, String symbol, Side side, BigDecimal quantity, BigDecimal limitPrice) {
        OrderRequest order = OrderRequestBuilder.anOrder().type(OrderType.LIMIT).symbol(symbol).side(side).quantity(quantity).limitPrice(limitPrice).build();
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(order)
                .build());
    }

    public static ApiResponse placeStopOrder(String token, String symbol, Side side, BigDecimal quantity, BigDecimal stopPrice) {
        OrderRequest order = OrderRequestBuilder.anOrder().type(OrderType.STOP).symbol(symbol).side(side).quantity(quantity).limitPrice(stopPrice).build();
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(order)
                .build());
    }

    public static ApiResponse placeOrder(String token, OrderRequest orderRequest) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(orderRequest)
                .build());
    }

    public static ApiResponse cancelOrder(String token, String orderId) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.DELETE)
                .path(BASE_PATH + "/{id}")
                .pathParam("id", orderId)
                .token(token)
                .build());
    }
}
