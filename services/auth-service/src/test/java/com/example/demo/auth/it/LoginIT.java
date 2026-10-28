package com.example.demo.auth.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.common.json.Json;

/** {@code POST /auth/login} (public). */
class LoginIT extends AuthITBase {

    @Test
    void loginIssuesAToken() {
        String body = api()
                .body("{\"username\":\"alice\",\"password\":\"password\"}")
                .post("/auth/login")
                .then().statusCode(200)
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("alice");
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims("alice", 3600));
        });
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        api()
                .body("{\"username\":\"alice\",\"password\":\"nope\"}")
                .post("/auth/login")
                .then().statusCode(401);
    }

    @Test
    void loginWithUnknownUserIsUnauthorized() {
        api()
                .body("{\"username\":\"nobody\",\"password\":\"password\"}")
                .post("/auth/login")
                .then().statusCode(401);
    }

    // Blank username or password fails @NotBlank validation -> 400.
    @ParameterizedTest(name = "blank login field -> 400: {0}")
    @ValueSource(strings = {
        "{\"username\":\"\",\"password\":\"password\"}",
        "{\"username\":\"alice\",\"password\":\"\"}"
    })
    void rejectsBlankLoginFieldsWith400(String body) {
        api()
                .body(body)
                .post("/auth/login")
                .then().statusCode(400);
    }
}
