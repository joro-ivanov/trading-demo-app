package com.example.demo.e2e.framework.service.order;

import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;

/**
 * Order-history (query) functionality of the order-service. Second {@code *Service} extending
 * the shared {@link BaseOrderService} (docs/DESIGN.md §8.4).
 */
public final class OrderHistoryService extends BaseOrderService {

    private OrderHistoryService() {
    }

    public static ApiResponse history(String token) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/history")
                .token(token)
                .build());
    }

    /** Full control (e.g. forged / missing / raw Authorization headers for security tests). */
    public static ApiResponse history(ApiRequest request) {
        return execute(serviceUrl(), request);
    }
}
