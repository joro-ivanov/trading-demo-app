package com.example.demo.auth.it;

import static com.example.demo.auth.it.harness.AuthFixtures.ALICE;
import static com.example.demo.auth.it.harness.AuthFixtures.LOGIN;
import static com.example.demo.auth.it.harness.AuthFixtures.SEEDED_PASSWORD;
import static com.example.demo.auth.it.harness.AuthFixtures.TOKEN_TTL_SECONDS;
import static com.example.demo.auth.it.harness.AuthFixtures.loginRequest;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.common.json.Json;

/** {@code POST /auth/login} (public). */
class LoginIT extends AuthITBase {

    private static final String WRONG_PASSWORD = "nope";
    private static final String UNKNOWN_USERNAME = "nobody";

    @Test
    void loginIssuesAToken() {
        String body = api()
                .body(loginRequest(ALICE.username(), SEEDED_PASSWORD))
                .post(LOGIN)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo(ALICE.username());
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims(ALICE.username(), TOKEN_TTL_SECONDS));
        });
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        api()
                .body(loginRequest(ALICE.username(), WRONG_PASSWORD))
                .post(LOGIN)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void loginWithUnknownUserIsUnauthorized() {
        api()
                .body(loginRequest(UNKNOWN_USERNAME, SEEDED_PASSWORD))
                .post(LOGIN)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    // Blank username or password fails @NotBlank validation -> 400.
    @ParameterizedTest(name = "blank login field -> 400: username={0}, password={1}")
    @CsvSource({
        "'', password",
        "alice, ''"
    })
    void rejectsBlankLoginFieldsWith400(String username, String password) {
        api()
                .body(loginRequest(username, password))
                .post(LOGIN)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
