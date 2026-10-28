package com.example.demo.auth.it;

import static com.example.demo.auth.it.harness.AuthFixtures.ALICE;
import static com.example.demo.auth.it.harness.AuthFixtures.LOGIN;
import static com.example.demo.auth.it.harness.AuthFixtures.REGISTER;
import static com.example.demo.auth.it.harness.AuthFixtures.TOKEN_TTL_SECONDS;
import static com.example.demo.auth.it.harness.AuthFixtures.emailOf;
import static com.example.demo.auth.it.harness.AuthFixtures.loginBody;
import static com.example.demo.auth.it.harness.AuthFixtures.registerBody;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.common.json.Json;

/** {@code POST /auth/register} (public). Each test registers its own distinct username/email. */
class RegisterIT extends AuthITBase {

    private static final String VALID_PASSWORD = "s3cret-pw";

    private static final String NEW_USER = "carol";
    private static final String RETURNING_USER = "dave";
    private static final String DUPLICATE_USERNAME = "erin";
    private static final String SECOND_EMAIL_FOR_DUPLICATE_USERNAME = "erin2";
    private static final String FIRST_OWNER_OF_SHARED_EMAIL = "frank";
    private static final String SECOND_OWNER_OF_SHARED_EMAIL = "grace";
    private static final String SHARED_EMAIL = "shared";
    private static final String NEW_EMAIL_FOR_SEEDED_USERNAME = "alice.new";

    @Test
    void registerCreatesUserAndReturnsToken() {
        String body = api()
                .body(registerBody(NEW_USER, emailOf(NEW_USER), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CREATED.value())
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo(NEW_USER);
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims(NEW_USER, TOKEN_TTL_SECONDS));
        });
    }

    @Test
    void registeredUserCanThenLogIn() {
        api()
                .body(registerBody(RETURNING_USER, emailOf(RETURNING_USER), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CREATED.value());

        String body = api()
                .body(loginBody(RETURNING_USER, VALID_PASSWORD))
                .post(LOGIN)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo(RETURNING_USER);
            softly.assertThat(claimsOf(response.token())).isEqualTo(new TokenClaims(RETURNING_USER, TOKEN_TTL_SECONDS));
        });
    }

    @Test
    void duplicateUsernameIsConflict() {
        api()
                .body(registerBody(DUPLICATE_USERNAME, emailOf(DUPLICATE_USERNAME), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CREATED.value());

        api()
                .body(registerBody(DUPLICATE_USERNAME, emailOf(SECOND_EMAIL_FOR_DUPLICATE_USERNAME), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CONFLICT.value());
    }

    @Test
    void duplicateEmailIsConflict() {
        api()
                .body(registerBody(FIRST_OWNER_OF_SHARED_EMAIL, emailOf(SHARED_EMAIL), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CREATED.value());

        api()
                .body(registerBody(SECOND_OWNER_OF_SHARED_EMAIL, emailOf(SHARED_EMAIL), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CONFLICT.value());
    }

    // Registering an already-seeded username (alice) is a conflict — proves the Flyway seed is live.
    @Test
    void registeringSeededUsernameIsConflict() {
        api()
                .body(registerBody(ALICE.username(), emailOf(NEW_EMAIL_FOR_SEEDED_USERNAME), VALID_PASSWORD))
                .post(REGISTER)
                .then().statusCode(HttpStatus.CONFLICT.value());
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
                .post(REGISTER)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
