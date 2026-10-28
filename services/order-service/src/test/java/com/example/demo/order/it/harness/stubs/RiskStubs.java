package com.example.demo.order.it.harness.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

/**
 * risk-service as seen by order-service's {@code RiskClient}:
 * {@code POST /risk/check} → {@code RiskCheckResponse(allowed, reason)}.
 */
public final class RiskStubs {

    private RiskStubs() {
    }

    public static final String CHECK = "/risk/check";

    /** Default: every order is within the owner's limits, so tests not about risk stay focused. */
    public static MappingBuilder allowsEverything() {
        return post(urlPathEqualTo(CHECK)).willReturn(okJson("{\"allowed\":true,\"reason\":null}"));
    }
}
