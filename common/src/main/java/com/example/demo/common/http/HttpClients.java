package com.example.demo.common.http;

import java.time.Duration;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Request factory for calls to other services. Every outbound call has explicit timeouts, so a
 * dependency that hangs becomes a {@code ResourceAccessException} (→ 503 via {@link Downstream})
 * instead of a request that hangs too. HTTP/1.1 via {@code HttpURLConnection}, so no extra dependency.
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static ClientHttpRequestFactory withTimeouts(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
