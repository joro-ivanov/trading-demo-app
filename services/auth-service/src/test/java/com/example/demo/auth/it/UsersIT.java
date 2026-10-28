package com.example.demo.auth.it;

import static com.example.demo.auth.it.harness.AuthFixtures.ALICE;
import static com.example.demo.auth.it.harness.AuthFixtures.BOB;
import static com.example.demo.auth.it.harness.AuthFixtures.DEMO;
import static com.example.demo.auth.it.harness.AuthFixtures.TEST_SUBJECT;
import static com.example.demo.auth.it.harness.AuthFixtures.USERS;
import static com.example.demo.auth.it.harness.AuthFixtures.userPath;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.UserResponse;
import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;

/** {@code GET /auth/users} and {@code GET /auth/users/{id}} (authenticated). */
class UsersIT extends AuthITBase {

    private static final String UNKNOWN_USER_ID = "no-such-id";
    /** A token whose expiry is already in the past. */
    private static final long EXPIRED_TOKEN_TTL_SECONDS = -60;

    @Test
    void userListRequiresAuthentication() {
        api()
                .get(USERS)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void userListIsReturnedWithAValidToken() {
        String body = api(validToken(TEST_SUBJECT))
                .get(USERS)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        // Exactly the Flyway seed (V1__users.sql): the harness removes users registered by other tests.
        assertThat(Json.readList(body, UserResponse.class)).containsExactlyInAnyOrder(ALICE, BOB, DEMO);
    }

    @Test
    void getUserByIdReturnsTheUser() {
        String body = api(validToken(TEST_SUBJECT))
                .get(userPath(ALICE.id()))
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        assertThat(Json.read(body, UserResponse.class)).isEqualTo(ALICE);
    }

    @Test
    void getUserByUnknownIdReturns404() {
        api(validToken(TEST_SUBJECT))
                .get(userPath(UNKNOWN_USER_ID))
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void anExpiredTokenIsRejectedWith401() {
        String expired = Jwt.issue(TEST_SUBJECT, SECRET, EXPIRED_TOKEN_TTL_SECONDS);

        api(expired)
                .get(USERS)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }
}
