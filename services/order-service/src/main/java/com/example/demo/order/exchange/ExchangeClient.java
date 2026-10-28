package com.example.demo.order.exchange;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.demo.common.http.Downstream;
import com.example.demo.common.http.HttpClients;
import com.example.demo.order.domain.OrderEntity;

/**
 * Routes orders to the upstream exchange over REST. In tests the exchange is a WireMock stub
 * (docs/DESIGN.md §9); in a real run it points at the configured {@code exchange.base-url}.
 */
@Component
public class ExchangeClient {

    private final RestClient restClient;

    public ExchangeClient(RestClient.Builder builder, ExchangeProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(HttpClients.withTimeouts(properties.connectTimeout(), properties.readTimeout()))
                .build();
    }

    /**
     * Routes the order to the exchange. An unreachable exchange is a 503 and an error or unreadable
     * answer a 502 (see {@link Downstream}); either way the caller's transaction rolls back.
     */
    public ExchangeAck route(OrderEntity order) {
        ExchangeAck ack;
        try {
            ack = restClient.post()
                    .uri("/exchange/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExchangeOrderRequest(order.getSymbol(), order.getSide().name(), order.getQuantity()))
                    .retrieve()
                    .body(ExchangeAck.class);
        } catch (RestClientException failure) {
            throw Downstream.failure("Exchange", failure);
        }
        if (ack == null) {
            throw Downstream.invalidResponse("Exchange", null);
        }
        return ack;
    }
}
