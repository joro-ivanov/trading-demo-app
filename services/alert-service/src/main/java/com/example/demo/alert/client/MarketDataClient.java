package com.example.demo.alert.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.alert.config.MarketDataProperties;
import com.example.demo.common.http.Downstream;
import com.example.demo.common.http.HttpClients;
import com.example.demo.common.security.Jwt;

/**
 * Validates symbols against market-data-service ({@code GET /prices/{symbol}}). A symbol is known
 * when market-data has a price for it. Failures are translated, never leaked as a 500:
 * 404 → 422 (unknown symbol); any other error, 5xx, I/O error or timeout → 503.
 */
@Component
public class MarketDataClient {

    private static final long TOKEN_TTL_SECONDS = 60;

    private final RestClient restClient;
    private final String secret;

    public MarketDataClient(RestClient.Builder builder, MarketDataProperties properties,
                            @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(HttpClients.withTimeouts(properties.connectTimeout(), properties.readTimeout()))
                .build();
        this.secret = secret;
    }

    /** Throws 422 when market-data does not know {@code symbol}, 503 when it cannot be asked. */
    public void requireKnownSymbol(String owner, String symbol) {
        try {
            restClient.get()
                    .uri("/prices/{symbol}", symbol)
                    .header("Authorization", "Bearer " + Jwt.issue(owner, secret, TOKEN_TTL_SECONDS))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown symbol: " + symbol);
        } catch (RestClientException failure) {
            throw Downstream.failure("Market data", failure);
        }
    }
}
