package com.example.demo.order.it.harness.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

/**
 * The upstream exchange's order-routing endpoint ({@code ExchangeClient}). The ack shape mirrors
 * {@code infra/wiremock/mappings/exchange-orders.json} / {@code ExchangeAck(status, exchangeOrderId)}.
 */
public final class ExchangeStubs {

    public static final String ORDERS = "/exchange/orders";

    private ExchangeStubs() {
    }

    public static MappingBuilder acceptsOrders() {
        return post(urlPathEqualTo(ORDERS))
                .willReturn(okJson("{\"status\":\"ACCEPTED\",\"exchangeOrderId\":\"EX-1\"}"));
    }

    public static MappingBuilder declinesOrders() {
        return post(urlPathEqualTo(ORDERS))
                .willReturn(okJson("{\"status\":\"DECLINED\",\"exchangeOrderId\":\"EX-0\"}"));
    }

    public static MappingBuilder failsWithServerError() {
        return post(urlPathEqualTo(ORDERS)).willReturn(serverError());
    }
}
