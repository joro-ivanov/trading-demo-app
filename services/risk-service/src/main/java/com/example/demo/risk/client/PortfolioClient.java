package com.example.demo.risk.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.demo.common.http.Downstream;
import com.example.demo.common.http.HttpClients;
import com.example.demo.common.security.Jwt;
import com.example.demo.risk.config.PortfolioProperties;

/**
 * Reads the owner's current holding of a symbol from portfolio-service, authenticating as the owner
 * with a short-lived token. Any failure (5xx, unexpected 4xx, connection refused, timeout) means the
 * check cannot be evaluated, so it surfaces as 503 rather than a guessed answer.
 */
@Component
public class PortfolioClient {

    private static final long TOKEN_TTL_SECONDS = 60;

    private final RestClient restClient;
    private final String secret;

    public PortfolioClient(RestClient.Builder builder, PortfolioProperties properties,
                           @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(HttpClients.withTimeouts(properties.connectTimeout(), properties.readTimeout()))
                .build();
        this.secret = secret;
    }

    public BigDecimal heldQuantity(String owner, String symbol) {
        try {
            HoldingResponse holding = restClient.get()
                    .uri("/portfolio/holdings/{symbol}", symbol)
                    .header("Authorization", "Bearer " + Jwt.issue(owner, secret, TOKEN_TTL_SECONDS))
                    .retrieve()
                    .body(HoldingResponse.class);
            if (holding == null || holding.quantity() == null) {
                throw Downstream.invalidResponse("Portfolio", null);
            }
            return holding.quantity();
        } catch (RestClientException failure) {
            throw Downstream.failure("Portfolio", failure);
        }
    }

    private record HoldingResponse(String symbol, BigDecimal quantity) {
    }
}
