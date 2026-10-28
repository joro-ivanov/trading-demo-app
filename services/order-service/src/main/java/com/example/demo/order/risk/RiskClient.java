package com.example.demo.order.risk;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.demo.common.http.Downstream;
import com.example.demo.common.http.HttpClients;
import com.example.demo.common.security.Jwt;
import com.example.demo.order.domain.Side;

/**
 * Asks risk-service whether the owner may place an order, authenticating as the owner with a
 * short-lived token. Fails closed: if the check cannot be evaluated (5xx, connection refused,
 * timeout, empty answer) the order is refused with 503 rather than routed unchecked.
 */
@Component
public class RiskClient {

    private static final long TOKEN_TTL_SECONDS = 60;

    private final RestClient restClient;
    private final String secret;

    public RiskClient(RestClient.Builder builder, RiskProperties properties,
                      @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(HttpClients.withTimeouts(properties.connectTimeout(), properties.readTimeout()))
                .build();
        this.secret = secret;
    }

    /** {@code price} may be null (no limit price and no market price known). */
    public RiskDecision check(String owner, String symbol, Side side, BigDecimal quantity, BigDecimal price) {
        try {
            RiskDecision decision = restClient.post()
                    .uri("/risk/check")
                    .header("Authorization", "Bearer " + Jwt.issue(owner, secret, TOKEN_TTL_SECONDS))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RiskCheckRequest(symbol, side, quantity, price))
                    .retrieve()
                    .body(RiskDecision.class);
            if (decision == null) {
                throw Downstream.invalidResponse("Risk check", null);
            }
            return decision;
        } catch (RestClientException failure) {
            throw Downstream.failure("Risk check", failure);
        }
    }

    private record RiskCheckRequest(String symbol, Side side, BigDecimal quantity, BigDecimal price) {
    }

    /** risk-service's answer: {@code reason} explains a refusal and is null when allowed. */
    public record RiskDecision(boolean allowed, String reason) {
    }
}
