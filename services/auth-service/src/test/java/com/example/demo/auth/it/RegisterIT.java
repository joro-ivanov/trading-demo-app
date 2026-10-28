package com.example.demo.auth.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.common.json.Json;

/** {@code POST /auth/register} (public). Each test registers its own distinct username/email. */
class RegisterIT extends AuthITBase {

    @Test
    void registerCreatesUserAndReturnsToken() {
        String body = api()
                .body("{\"username\":\"carol\",\"email\":\"carol@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(201)
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("carol");
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims("carol", 3600));
        });
    }

    @Test
    void registeredUserCanThenLogIn() {
        api()
                .body("{\"username\":\"dave\",\"email\":\"dave@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(201);

        String body = api()
                .body("{\"username\":\"dave\",\"password\":\"s3cret-pw\"}")
                .post("/auth/login")
                .then().statusCode(200)
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("dave");
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims("dave", 3600));
        });
    }

    @Test
    void duplicateUsernameIsConflict() {
        api()
                .body("{\"username\":\"erin\",\"email\":\"erin@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(201);

        api()
                .body("{\"username\":\"erin\",\"email\":\"erin2@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(409);
    }

    @Test
    void duplicateEmailIsConflict() {
        api()
                .body("{\"username\":\"frank\",\"email\":\"shared@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(201);

        api()
                .body("{\"username\":\"grace\",\"email\":\"shared@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(409);
    }

    // Registering an already-seeded username (alice) is a conflict — proves the Flyway seed is live.
    @Test
    void registeringSeededUsernameIsConflict() {
        api()
                .body("{\"username\":\"alice\",\"email\":\"alice.new@demo.example.com\",\"password\":\"s3cret-pw\"}")
                .post("/auth/register")
                .then().statusCode(409);
    }

    // EP/BVA invalid partitions: blank/short/too-long/bad-pattern username, bad/blank email, short/blank
    // password. Each fails bean validation -> 400.
    @ParameterizedTest(name = "invalid registration -> 400: {0}")
    @ValueSource(strings = {
        "{\"username\":\"\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"ab\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"this_username_is_way_too_long\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"bad name\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"bad-hyphen\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"not-an-email\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"a@b.com\",\"password\":\"short\"}",
        "{\"username\":\"good\",\"email\":\"a@b.com\",\"password\":\"\"}"
    })
    void rejectsInvalidRegistrationWith400(String body) {
        api()
                .body(body)
                .post("/auth/register")
                .then().statusCode(400);
    }
}
