package com.example.demo.e2e.api;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.ErrorResponse;
import com.example.demo.e2e.framework.service.auth.AuthService;

import io.qameta.allure.Allure;

/**
 * Auth API e2e (docs/DESIGN.md §7.2, §8) — registration + negative login against the running stack.
 * Black-box, not market-hours gated. The exhaustive validation matrix lives in AuthServiceIT; here we
 * prove the deployed wiring: register → login, and the representative 409 / 400 / 401 paths.
 */
@Tag("api")
@ExtendWith(TestLogArtifacts.class)
class AuthTest extends BaseApiTest {

    @Test
    void registerThenLoginSucceeds() {
        String username = uniqueUsername();

        Allure.step("Register a new user");
        ApiResponse registered = AuthService.register(username, emailFor(username), REGISTERED_USER_PASSWORD);
        Login registeredView = registered.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(registered.statusCode()).isEqualTo(201);
            softly.assertThat(registeredView.username()).isEqualTo(username);
            softly.assertThat(registeredView.token()).isNotBlank();
        });

        Allure.step("The new user can then log in with those credentials");
        ApiResponse loggedIn = AuthService.login(username, REGISTERED_USER_PASSWORD);
        Login loggedInView = loggedIn.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(loggedIn.statusCode()).isEqualTo(200);
            softly.assertThat(loggedInView.username()).isEqualTo(username);
            softly.assertThat(loggedInView.token()).isNotBlank();
        });
    }

    @Test
    void duplicateUsernameIsRejectedWith409() {
        String username = uniqueUsername();

        Allure.step("Register the username once");
        ApiResponse firstRegistration = AuthService.register(username, emailFor(username), REGISTERED_USER_PASSWORD);
        Login firstRegistrationView = firstRegistration.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(firstRegistration.statusCode()).isEqualTo(201);
            softly.assertThat(firstRegistrationView.username()).isEqualTo(username);
            softly.assertThat(firstRegistrationView.token()).isNotBlank();
        });

        // Expected for this demo app: registration explicitly reveals that the username is already
        // taken. In a hardened production system this would likely be generalized to reduce account
        // enumeration, but here the clearer UX is an intentional tradeoff.
        Allure.step("Registering it again (different email) is a 409 conflict");
        ApiResponse duplicateRegistration = AuthService.register(
                username,
                username + ".2@" + EMAIL_DOMAIN,
                REGISTERED_USER_PASSWORD);
        ErrorResponse duplicateRegistrationProblem = duplicateRegistration.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(duplicateRegistration.statusCode()).isEqualTo(409);
            softly.assertThat(duplicateRegistrationProblem.error()).isEqualTo("Conflict");
            softly.assertThat(duplicateRegistrationProblem.status()).isEqualTo(409);
            softly.assertThat(duplicateRegistrationProblem.message()).isEqualTo("Username already taken");
            softly.assertThat(duplicateRegistrationProblem.path()).isEqualTo("/auth/register");
        });
    }

    // Representative invalid partitions (short username, bad email, short password) — each is a 400.
    @ParameterizedTest(name = "invalid registration ({0},{1},{2}) -> 400")
    @CsvSource({
        "ab,good@demo.example.com,s3cret-pw",
        "gooduser,not-an-email,s3cret-pw",
        "gooduser,good@demo.example.com,short"
    })
    void invalidRegistrationIsRejectedWith400(String username, String email, String password) {
        Allure.step("Registration with invalid fields is rejected");
        ApiResponse response = AuthService.register(username, email, password);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(problem.error()).isEqualTo("Bad Request");
            softly.assertThat(problem.status()).isEqualTo(400);
            softly.assertThat(problem.message()).isEqualTo("Invalid request content.");
            softly.assertThat(problem.path()).isEqualTo("/auth/register");
        });
    }

    @Test
    void loginWithWrongPasswordIsRejectedWith401() {
        Allure.step("A wrong password for a known user is 401");
        ApiResponse response = AuthService.login(username, "definitely-wrong");
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("Invalid credentials");
            softly.assertThat(problem.path()).isEqualTo("/auth/login");
        });
    }

    @Test
    void loginWithUnknownUserIsRejectedWith401() {
        Allure.step("An unknown user is 401");
        ApiResponse response = AuthService.login("no-such-user", REGISTERED_USER_PASSWORD);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(401);
            softly.assertThat(problem.error()).isEqualTo("Unauthorized");
            softly.assertThat(problem.status()).isEqualTo(401);
            softly.assertThat(problem.message()).isEqualTo("Invalid credentials");
            softly.assertThat(problem.path()).isEqualTo("/auth/login");
        });
    }

    @Test
    void loginWithBlankPasswordIsRejectedWith400() {
        Allure.step("A blank password fails validation with 400");
        ApiResponse response = AuthService.login(username, "");
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(problem.error()).isEqualTo("Bad Request");
            softly.assertThat(problem.status()).isEqualTo(400);
            softly.assertThat(problem.message()).isEqualTo("Invalid request content.");
            softly.assertThat(problem.path()).isEqualTo("/auth/login");
        });
    }
}
