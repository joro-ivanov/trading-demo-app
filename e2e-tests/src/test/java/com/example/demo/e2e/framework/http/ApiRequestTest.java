package com.example.demo.e2e.framework.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.demo.e2e.framework.model.LoginRequest;
import com.example.demo.testing.RawJsonPayload;

class ApiRequestTest {

    private static final String PATH = "/orders";
    private static final String ANY_PATH = "/x";
    private static final String TOKEN = "jwt-123";
    private static final String HEADER_NAME = "X-Trace";
    private static final String HEADER_VALUE = "abc";
    private static final String QUERY_PARAM_NAME = "limit";
    private static final String QUERY_PARAM_VALUE = "10";
    private static final String PATH_PARAM_NAME = "id";
    private static final String PATH_PARAM_VALUE = "9";
    private static final String COOKIE_NAME = "session";
    private static final String COOKIE_VALUE = "s1";
    private static final String USERNAME = "trader";
    private static final String PASSWORD = "secret";
    private static final LoginRequest BODY = new LoginRequest(USERNAME, PASSWORD);
    private static final String HAND_WRITTEN_BODY = "payload";
    private static final String NEW_HEADER_NAME = "a";
    private static final String NEW_HEADER_VALUE = "b";

    @Test
    void buildsRequestWithExactValues() {
        ApiRequest request = ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(PATH)
                .token(TOKEN)
                .header(HEADER_NAME, HEADER_VALUE)
                .queryParam(QUERY_PARAM_NAME, QUERY_PARAM_VALUE)
                .pathParam(PATH_PARAM_NAME, PATH_PARAM_VALUE)
                .cookie(COOKIE_NAME, COOKIE_VALUE)
                .body(BODY)
                .build();

        assertSoftly(softly -> {
            softly.assertThat(request.method()).isEqualTo(HttpMethod.POST);
            softly.assertThat(request.path()).isEqualTo(PATH);
            softly.assertThat(request.token()).isEqualTo(TOKEN);
            softly.assertThat(request.headers()).containsExactly(entry(HEADER_NAME, HEADER_VALUE));
            softly.assertThat(request.queryParams()).containsExactly(entry(QUERY_PARAM_NAME, QUERY_PARAM_VALUE));
            softly.assertThat(request.pathParams()).containsExactly(entry(PATH_PARAM_NAME, PATH_PARAM_VALUE));
            softly.assertThat(request.cookies()).containsExactly(entry(COOKIE_NAME, COOKIE_VALUE));
            softly.assertThat(request.body()).isEqualTo(BODY);
        });
    }

    @Test
    void bodyRejectsAHandWrittenString() {
        ApiRequest.Builder builder = ApiRequest.builder().method(HttpMethod.POST).path(PATH);

        assertThatThrownBy(() -> builder.body(HAND_WRITTEN_BODY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiRequest.STRING_BODY_REJECTED);
    }

    @Test
    @RawJsonPayload(reason = "verifies the raw-body opt-in itself: broken JSON must be kept verbatim, not serialized")
    void rawBodyKeepsTheHandWrittenJsonVerbatim() {
        String malformedJson = "{\"quantity\": }";
        ApiRequest request = ApiRequest.builder().method(HttpMethod.POST).path(PATH).rawBody(malformedJson).build();

        assertThat(request.body()).isEqualTo(new RawBody(malformedJson));
    }

    @Test
    void headersMapIsImmutable() {
        ApiRequest request = ApiRequest.builder().method(HttpMethod.GET).path(ANY_PATH).build();

        assertThatThrownBy(() -> request.headers().put(NEW_HEADER_NAME, NEW_HEADER_VALUE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    void buildsEveryMethod(HttpMethod method) {
        ApiRequest request = ApiRequest.builder().method(method).path(ANY_PATH).build();

        assertThat(request.method()).isEqualTo(method);
    }
}
