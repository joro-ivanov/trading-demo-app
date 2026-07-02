package com.example.demo.order.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.demo.order.domain.OrderEntity;

/**
 * Routes orders to the upstream exchange over REST. In tests the exchange is a WireMock stub
 * (docs/DESIGN.md §9); in a real run it points at the configured {@code exchange.base-url}.
 */
@Component
public class ExchangeClient {

    private final RestClient restClient;

    public ExchangeClient(RestClient.Builder builder, @Value("${exchange.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ExchangeAck route(OrderEntity order) {
        return restClient.post()
                .uri("/exchange/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExchangeOrderRequest(order.getSymbol(), order.getSide().name(), order.getQuantity()))
                .retrieve()
                .body(ExchangeAck.class);
    }
}
