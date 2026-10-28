package com.example.demo.e2e.api;

import static com.example.demo.e2e.framework.support.ApiErrorMessages.INVALID_CREDENTIALS;
import static com.example.demo.e2e.framework.support.ApiErrorMessages.INVALID_REQUEST_CONTENT;
import static com.example.demo.e2e.framework.support.ApiPaths.AUTH_LOGIN;
import static com.example.demo.e2e.framework.support.ApiPaths.AUTH_REGISTER;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.BAD_REQUEST_REASON;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CONFLICT;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CONFLICT_REASON;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.OK;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNAUTHORIZED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNAUTHORIZED_REASON;

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
import com.example.demo.e2e.framework.support.Tokens;

import io.qameta.allure.Allure;

/**
 * Auth API e2e (docs/DESIGN.md §7.2, §8) — registration + negative login against the running stack.
 * Black-box, not market-hours gated. The exhaustive validation matrix lives in the auth-service ITs (RegisterIT/LoginIT); here we
 * prove the deployed wiring: register → login, and the representative 409 / 400 / 401 paths.
 */
@Tag("api")
@ExtendWith(TestLogArtifacts.class)
class AuthTest extends BaseApiTest {

    /** Turns a username into a second, different email address on the same domain. */
    private static final String SECOND_EMAIL_SUFFIX = ".2@";
    private static final String USERNAME_TAKEN = "Username already taken";
    private static final String WRONG_PASSWORD = "definitely-wrong";
    private static final String UNKNOWN_USERNAME = "no-such-user";

    @Test
    void registerThenLoginSucceeds() {
        String username = uniqueUsername();

        Allure.step("Register a new user");
        ApiResponse registered = AuthService.register(username, emailFor(username), REGISTERED_USER_PASSWORD);
        Login registeredView = registered.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(registered.statusCode()).isEqualTo(CREATED);
            softly.assertThat(registeredView.username()).isEqualTo(username);
            softly.assertThat(Tokens.claims(registeredView.token()).sub()).isEqualTo(username);
            softly.assertThat(Tokens.claims(registeredView.token()).iss()).isEqualTo(Tokens.AUTH_SERVICE_ISSUER);
        });

        Allure.step("The new user can then log in with those credentials");
        ApiResponse loggedIn = AuthService.login(username, REGISTERED_USER_PASSWORD);
        Login loggedInView = loggedIn.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(loggedIn.statusCode()).isEqualTo(OK);
            softly.assertThat(loggedInView.username()).isEqualTo(username);
            softly.assertThat(Tokens.claims(loggedInView.token()).sub()).isEqualTo(username);
            softly.assertThat(Tokens.claims(loggedInView.token()).iss()).isEqualTo(Tokens.AUTH_SERVICE_ISSUER);
        });
    }

    @Test
    void duplicateUsernameIsRejectedWith409() {
        String username = uniqueUsername();

        Allure.step("Register the username once");
        ApiResponse firstRegistration = AuthService.register(username, emailFor(username), REGISTERED_USER_PASSWORD);
        Login firstRegistrationView = firstRegistration.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(firstRegistration.statusCode()).isEqualTo(CREATED);
            softly.assertThat(firstRegistrationView.username()).isEqualTo(username);
            softly.assertThat(Tokens.claims(firstRegistrationView.token()).sub()).isEqualTo(username);
            softly.assertThat(Tokens.claims(firstRegistrationView.token()).iss()).isEqualTo(Tokens.AUTH_SERVICE_ISSUER);
        });

        // Expected for this demo app: registration explicitly reveals that the username is already
        // taken. In a hardened production system this would likely be generalized to reduce account
        // enumeration, but here the clearer UX is an intentional tradeoff.
        Allure.step("Registering it again (different email) is a 409 conflict");
        ApiResponse duplicateRegistration = AuthService.register(
                username,
                username + SECOND_EMAIL_SUFFIX + EMAIL_DOMAIN,
                REGISTERED_USER_PASSWORD);
        ErrorResponse duplicateRegistrationProblem = duplicateRegistration.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(duplicateRegistration.statusCode()).isEqualTo(CONFLICT);
            softly.assertThat(duplicateRegistrationProblem.error()).isEqualTo(CONFLICT_REASON);
            softly.assertThat(duplicateRegistrationProblem.status()).isEqualTo(CONFLICT);
            softly.assertThat(duplicateRegistrationProblem.message()).isEqualTo(USERNAME_TAKEN);
            softly.assertThat(duplicateRegistrationProblem.path()).isEqualTo(AUTH_REGISTER);
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
            softly.assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.error()).isEqualTo(BAD_REQUEST_REASON);
            softly.assertThat(problem.status()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.message()).isEqualTo(INVALID_REQUEST_CONTENT);
            softly.assertThat(problem.path()).isEqualTo(AUTH_REGISTER);
        });
    }

    @Test
    void loginWithWrongPasswordIsRejectedWith401() {
        Allure.step("A wrong password for a known user is 401");
        ApiResponse response = AuthService.login(username, WRONG_PASSWORD);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.error()).isEqualTo(UNAUTHORIZED_REASON);
            softly.assertThat(problem.status()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.message()).isEqualTo(INVALID_CREDENTIALS);
            softly.assertThat(problem.path()).isEqualTo(AUTH_LOGIN);
        });
    }

    @Test
    void loginWithUnknownUserIsRejectedWith401() {
        Allure.step("An unknown user is 401");
        ApiResponse response = AuthService.login(UNKNOWN_USERNAME, REGISTERED_USER_PASSWORD);
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.error()).isEqualTo(UNAUTHORIZED_REASON);
            softly.assertThat(problem.status()).isEqualTo(UNAUTHORIZED);
            softly.assertThat(problem.message()).isEqualTo(INVALID_CREDENTIALS);
            softly.assertThat(problem.path()).isEqualTo(AUTH_LOGIN);
        });
    }

    @Test
    void loginWithBlankPasswordIsRejectedWith400() {
        Allure.step("A blank password fails validation with 400");
        ApiResponse response = AuthService.login(username, "");
        ErrorResponse problem = response.as(ErrorResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.error()).isEqualTo(BAD_REQUEST_REASON);
            softly.assertThat(problem.status()).isEqualTo(BAD_REQUEST);
            softly.assertThat(problem.message()).isEqualTo(INVALID_REQUEST_CONTENT);
            softly.assertThat(problem.path()).isEqualTo(AUTH_LOGIN);
        });
    }
}
