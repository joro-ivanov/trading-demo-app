package com.example.demo.e2e.framework.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApiRequestTest {

    @Test
    void buildsRequestWithExactValues() {
        ApiRequest request = ApiRequest.builder()
                .method(HttpMethod.POST)
                .path("/orders")
                .token("jwt-123")
                .header("X-Trace", "abc")
                .queryParam("limit", "10")
                .pathParam("id", "9")
                .cookie("session", "s1")
                .body("payload")
                .build();

        assertSoftly(softly -> {
            softly.assertThat(request.method()).isEqualTo(HttpMethod.POST);
            softly.assertThat(request.path()).isEqualTo("/orders");
            softly.assertThat(request.token()).isEqualTo("jwt-123");
            softly.assertThat(request.headers()).containsExactly(entry("X-Trace", "abc"));
            softly.assertThat(request.queryParams()).containsExactly(entry("limit", "10"));
            softly.assertThat(request.pathParams()).containsExactly(entry("id", "9"));
            softly.assertThat(request.cookies()).containsExactly(entry("session", "s1"));
            softly.assertThat(request.body()).isEqualTo("payload");
        });
    }

    @Test
    void headersMapIsImmutable() {
        ApiRequest request = ApiRequest.builder().method(HttpMethod.GET).path("/x").build();

        assertThatThrownBy(() -> request.headers().put("a", "b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    void buildsEveryMethod(HttpMethod method) {
        ApiRequest request = ApiRequest.builder().method(method).path("/x").build();

        assertThat(request.method()).isEqualTo(method);
    }
}
