package com.example.demo.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.auth.it.harness.AuthITBase;
import com.example.demo.auth.web.AuthDtos.UserResponse;
import com.example.demo.common.json.Json;
import com.example.demo.common.security.Jwt;

/** {@code GET /auth/users} and {@code GET /auth/users/{id}} (authenticated). */
class UsersIT extends AuthITBase {

    @Test
    void userListRequiresAuthentication() {
        api()
                .get("/auth/users")
                .then().statusCode(401);
    }

    @Test
    void userListIsReturnedWithAValidToken() {
        String body = api(validToken("tester"))
                .get("/auth/users")
                .then().statusCode(200)
                .extract().asString();

        // Exactly the Flyway seed (V1__users.sql): the harness removes users registered by other tests.
        assertThat(Json.readList(body, UserResponse.class)).containsExactlyInAnyOrder(
                new UserResponse("u1", "alice", "Alice Trader"),
                new UserResponse("u2", "bob", "Bob Trader"),
                new UserResponse("u3", "demo", "Demo User"));
    }

    @Test
    void getUserByIdReturnsTheUser() {
        // u1 is alice's seeded id (V1__users.sql).
        String body = api(validToken("tester"))
                .get("/auth/users/u1")
                .then().statusCode(200)
                .extract().asString();

        assertThat(Json.read(body, UserResponse.class)).isEqualTo(new UserResponse("u1", "alice", "Alice Trader"));
    }

    @Test
    void getUserByUnknownIdReturns404() {
        api(validToken("tester"))
                .get("/auth/users/no-such-id")
                .then().statusCode(404);
    }

    @Test
    void anExpiredTokenIsRejectedWith401() {
        String expired = Jwt.issue("tester", SECRET, -60);

        api(expired)
                .get("/auth/users")
                .then().statusCode(401);
    }
}
