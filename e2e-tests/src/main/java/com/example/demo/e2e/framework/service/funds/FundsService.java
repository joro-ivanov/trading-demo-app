package com.example.demo.e2e.framework.service.funds;

import java.math.BigDecimal;
import java.util.Map;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * Cash funds, hosted by portfolio-service ({@code /funds/*}). Dumb pipe, static methods, no
 * validation (docs/DESIGN.md §8.4).
 */
public final class FundsService extends BaseService {

    private FundsService() {
    }

    public static ApiResponse balance(String token) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path("/funds/balance")
                .token(token)
                .build());
    }

    public static ApiResponse deposit(String token, BigDecimal amount) {
        return move(token, amount, "/funds/deposit");
    }

    public static ApiResponse withdraw(String token, BigDecimal amount) {
        return move(token, amount, "/funds/withdraw");
    }

    private static ApiResponse move(String token, BigDecimal amount, String path) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(path)
                .token(token)
                .body(Map.of("amount", amount))
                .build());
    }

    private static String serviceUrl() {
        return Config.get().get("portfolio.base.url");
    }
}
