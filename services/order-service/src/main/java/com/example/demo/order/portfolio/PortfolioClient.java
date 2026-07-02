package com.example.demo.order.portfolio;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.demo.common.security.Jwt;

/**
 * Looks up the current holding for a symbol (the pre-trade short-sell guard). The portfolio is
 * per-user, so the lookup must be scoped to the order's owner: order-service mints a short-lived
 * token with the <i>owner</i> as subject (from the shared secret) so the portfolio returns that
 * user's holding. In tests the endpoint is a WireMock stub.
 */
@Component
public class PortfolioClient {

    private final RestClient restClient;
    private final String secret;

    public PortfolioClient(RestClient.Builder builder,
                           @Value("${portfolio.base-url}") String baseUrl,
                           @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.secret = secret;
    }

    public BigDecimal heldQuantity(String owner, String symbol) {
        HoldingResponse holding = restClient.get()
                .uri("/portfolio/holdings/{symbol}", symbol)
                .header("Authorization", "Bearer " + Jwt.issue(owner, secret, 60))
                .retrieve()
                .body(HoldingResponse.class);
        return holding == null ? BigDecimal.ZERO : holding.quantity();
    }

    /** The owner's available cash (the pre-trade funds guard's lookup). */
    public BigDecimal availableCash(String owner) {
        BalanceResponse balance = restClient.get()
                .uri("/funds/balance")
                .header("Authorization", "Bearer " + Jwt.issue(owner, secret, 60))
                .retrieve()
                .body(BalanceResponse.class);
        return balance == null ? BigDecimal.ZERO : balance.cash();
    }

    private record HoldingResponse(String symbol, BigDecimal quantity) {
    }

    private record BalanceResponse(BigDecimal cash) {
    }
}
