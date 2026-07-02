package com.example.demo.e2e.framework.service.marketdata;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.HttpMethod;
import com.example.demo.e2e.framework.service.BaseService;

/**
 * Market-data has a single functionality (latest prices), so per docs/DESIGN.md §8.4 it extends
 * {@code BaseService} directly — no {@code Base*Service}.
 */
public final class MarketDataService extends BaseService {

    private static final String BASE_PATH = "/prices";

    private MarketDataService() {
    }

    public static ApiResponse latestPrice(String token, String symbol) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/{symbol}")
                .pathParam("symbol", symbol)
                .token(token)
                .build());
    }

    private static String serviceUrl() {
        return Config.get().get("market-data.base.url");
    }
}
