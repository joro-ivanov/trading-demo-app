package com.example.demo.order.marketdata;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.example.demo.common.security.Jwt;

/**
 * Reads the latest cached price for a symbol from market-data-service (used to value a BUY for the
 * pre-trade funds guard). Authenticates with a short-lived service token. An unknown symbol (404)
 * means "no price yet" — returns empty. In tests the endpoint is a WireMock stub.
 */
@Component
public class MarketPriceClient {

    private final RestClient restClient;
    private final String secret;

    public MarketPriceClient(RestClient.Builder builder,
                             @Value("${market-data.base-url}") String baseUrl,
                             @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.secret = secret;
    }

    public Optional<BigDecimal> latestPrice(String symbol) {
        try {
            PriceResponse price = restClient.get()
                    .uri("/prices/{symbol}", symbol)
                    .header("Authorization", "Bearer " + Jwt.issue("order-service", secret, 60))
                    .retrieve()
                    .body(PriceResponse.class);
            return price == null ? Optional.empty() : Optional.ofNullable(price.price());
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        }
    }

    private record PriceResponse(String symbol, BigDecimal price) {
    }
}
