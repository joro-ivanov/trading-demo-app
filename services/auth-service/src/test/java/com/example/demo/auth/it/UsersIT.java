package com.example.demo.auth.it;

import static com.example.demo.auth.it.harness.AuthFixtures.ALICE;
import static com.example.demo.auth.it.harness.AuthFixtures.BOB;
import static com.example.demo.auth.it.harness.AuthFixtures.DEMO;
import static com.example.demo.auth.it.harness.AuthFixtures.USERS;
import static com.example.demo.auth.it.harness.AuthFixtures.userPath;
import static com.example.demo.testing.support.auth.TestJwt.expired;
import static com.example.demo.testing.support.auth.TestJwt.token;
import static com.example.demo.testing.support.fixtures.TestUsers.TESTER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.UserResponse;
import com.example.demo.common.json.Json;

/** {@code GET /auth/users} and {@code GET /auth/users/{id}} (authenticated). */
class UsersIT extends AuthITBase {

    private static final String UNKNOWN_USER_ID = "no-such-id";

    @Test
    void userListRequiresAuthentication() {
        api()
                .get(USERS)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void userListIsReturnedWithAValidToken() {
        String body = api(token(TESTER))
                .get(USERS)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        // Exactly the Flyway seed (V1__users.sql): the harness removes users registered by other tests.
        assertThat(Json.readList(body, UserResponse.class)).containsExactlyInAnyOrder(ALICE, BOB, DEMO);
    }

    @Test
    void getUserByIdReturnsTheUser() {
        String body = api(token(TESTER))
                .get(userPath(ALICE.id()))
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        assertThat(Json.read(body, UserResponse.class)).isEqualTo(ALICE);
    }

    @Test
    void getUserByUnknownIdReturns404() {
        api(token(TESTER))
                .get(userPath(UNKNOWN_USER_ID))
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void anExpiredTokenIsRejectedWith401() {
        String expired = expired(TESTER);

        api(expired)
                .get(USERS)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }
}
