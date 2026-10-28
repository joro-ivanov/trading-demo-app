package com.example.demo.e2e.framework.http;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.demo.e2e.framework.support.RawJsonPayload;

/**
 * Immutable description of an HTTP request, built fluently via {@link #builder()}.
 *
 * <p>Records-with-builders is intentional: the record stays immutable while the builder is the
 * ergonomic constructor (docs/DESIGN.md §8.1). The builder performs no validation of
 * values — a test may set a blank path or a missing token so that negative scenarios reach the
 * real server (docs/DESIGN.md §8.2). The one guard is on the body: {@link Builder#body(Object)}
 * takes a DTO and rejects a {@code String}, because tests never hand-write JSON. A payload no DTO
 * can express (wrong JSON type, missing vs {@code null}, broken JSON) goes through
 * {@link Builder#rawBody(String)}, which only a {@code @RawJsonPayload} test may call.
 */
public record ApiRequest(
        String path,
        HttpMethod method,
        Object body,
        Map<String, String> headers,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        Map<String, String> cookies,
        String token) {

    public static Builder builder() {
        return new Builder();
    }

    /** Message of the {@link IllegalArgumentException} thrown when {@link Builder#body(Object)} gets a {@code String}. */
    public static final String STRING_BODY_REJECTED =
            "body() takes a DTO; send a hand-written payload with rawBody(...) from a @RawJsonPayload test";

    public static final class Builder {
        private String path;
        private HttpMethod method;
        private Object body;
        private String token;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> pathParams = new LinkedHashMap<>();
        private final Map<String, String> queryParams = new LinkedHashMap<>();
        private final Map<String, String> cookies = new LinkedHashMap<>();

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        /** A DTO, serialized to JSON when sent. A {@code String} is rejected: use {@link #rawBody(String)}. */
        public Builder body(Object body) {
            if (body instanceof String) {
                throw new IllegalArgumentException(STRING_BODY_REJECTED);
            }
            this.body = body;
            return this;
        }

        /** A hand-written JSON body, sent verbatim. Only for invalid-payload scenarios. */
        @RawJsonPayload(reason = "the opt-in through which a negative test sends a payload no DTO can express")
        public Builder rawBody(String json) {
            this.body = new RawBody(json);
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> values) {
            headers.putAll(values);
            return this;
        }

        public Builder pathParam(String name, String value) {
            pathParams.put(name, value);
            return this;
        }

        public Builder queryParam(String name, String value) {
            queryParams.put(name, value);
            return this;
        }

        public Builder cookie(String name, String value) {
            cookies.put(name, value);
            return this;
        }

        public ApiRequest build() {
            return new ApiRequest(
                    path,
                    method,
                    body,
                    Map.copyOf(headers),
                    Map.copyOf(pathParams),
                    Map.copyOf(queryParams),
                    Map.copyOf(cookies),
                    token);
        }
    }
}
