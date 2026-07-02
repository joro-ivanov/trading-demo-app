package com.example.demo.e2e.framework.service.portfolio;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * Portfolio has a single endpoint ({@code GET /portfolio}), so it extends {@code BaseService}
 * directly (docs/DESIGN.md §8.4). Dumb pipe, static methods, no validation.
 */
public final class PortfolioService extends BaseService {

    private static final String BASE_PATH = "/portfolio";

    private PortfolioService() {
    }

    public static ApiResponse positions(String token) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH)
                .token(token)
                .build());
    }

    public static ApiResponse recentFills(String token) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/fills")
                .token(token)
                .build());
    }

    private static String serviceUrl() {
        return Config.get().get("portfolio.base.url");
    }
}
