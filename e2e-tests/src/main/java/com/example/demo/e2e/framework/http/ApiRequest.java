package com.example.demo.e2e.framework.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable description of an HTTP request, built fluently via {@link #builder()}.
 *
 * <p>Records-with-builders is intentional: the record stays immutable while the builder is the
 * ergonomic constructor (docs/DESIGN.md §8.1). The builder performs <b>no validation</b> — a
 * test may set a blank path, a missing token, or a malformed body so that negative scenarios
 * reach the real server (docs/DESIGN.md §8.2).
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

        public Builder body(Object body) {
            this.body = body;
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
