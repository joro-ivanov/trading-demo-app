package com.example.demo.e2e.ui;

import static com.example.demo.e2e.framework.support.HttpStatusCodes.CONFLICT;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static com.example.demo.e2e.framework.support.HttpStatusCodes.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

import com.example.demo.e2e.framework.pages.LoginPage;
import com.example.demo.e2e.framework.pages.RegistrationPage;

import io.qameta.allure.Allure;

/**
 * Auth UI e2e: negative login + registration field validation, in a real browser. Network
 * interception ({@link RegistrationPage}/{@link LoginPage}) makes the failure scenarios
 * deterministic; the happy-path registration runs against the live stack. Not market-hours gated
 * (auth is not order placement).
 */
class AuthNegativeTest extends BaseUiTest {

    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String VALID_USERNAME = "validuser";
    private static final String INVALID_EMAIL = "not-an-email";
    private static final String VALID_EMAIL = "valid@" + EMAIL_DOMAIN;
    /** Shorter than the registration form's minimum password length. */
    private static final String SHORT_PASSWORD = "short";
    private static final String MISMATCHED_CONFIRMATION = "different";
    private static final String PASSWORDS_DO_NOT_MATCH = "Passwords do not match.";
    /** Turns a username into a second, different email address on the same domain. */
    private static final String NEW_EMAIL_SUFFIX = ".new@";
    private static final String USERNAME_TAKEN_MESSAGE = "That username is already taken.";

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void rejectedLoginShowsAnErrorAndDoesNotEnterTheApp() {
        LoginPage login = openLoginPage();
        Allure.step("The login POST is rejected with 401");
        login.stubLogin(UNAUTHORIZED);
        login.fillCredentials(username, WRONG_PASSWORD).submit();

        login.awaitLoginError();
        assertThat(login.loginRequestCount()).isEqualTo(1);
    }

    // ── registration field validation (real browser enforces HTML5 rules) ─────

    @Test
    void invalidEmailIsBlockedByNativeValidationAndSendsNoRequest() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(CREATED);
        Allure.step("Fill a syntactically invalid email and submit");
        register.fill(VALID_USERNAME, INVALID_EMAIL, REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD).submit();

        assertSoftly(softly -> {
            softly.assertThat(register.emailIsValid()).isFalse();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void shortPasswordIsBlockedByNativeValidation() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(CREATED);
        Allure.step("A password under the 8-char minimum is invalid and submits nothing");
        register.fill(VALID_USERNAME, VALID_EMAIL, SHORT_PASSWORD, SHORT_PASSWORD).submit();

        assertSoftly(softly -> {
            softly.assertThat(register.passwordIsValid()).isFalse();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void confirmMismatchDisablesSubmitAndShowsAnError() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(CREATED);
        Allure.step("Confirm password differs from password");
        register.fill(VALID_USERNAME, VALID_EMAIL, REGISTERED_USER_PASSWORD, MISMATCHED_CONFIRMATION);

        register.awaitRegisterError(PASSWORDS_DO_NOT_MATCH);
        assertSoftly(softly -> {
            softly.assertThat(register.submitDisabled()).isTrue();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void duplicateUsernameShowsAConflictMessage() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(CONFLICT);
        Allure.step("The register POST returns 409");
        register.fill(username, username + NEW_EMAIL_SUFFIX + EMAIL_DOMAIN, REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD)
                .submit();

        register.awaitRegisterError(USERNAME_TAKEN_MESSAGE);
    }

    // ── registration happy path (real backend) ────────────────────────────────

    @Test
    void registeringANewUserLandsOnTheDashboard() {
        RegistrationPage register = openRegistrationPage();
        String username = uniqueUsername();
        Allure.step("Register a brand-new user against the live stack");
        register.fill(username, emailFor(username), REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD).submit();

        register.awaitDashboard();
    }
}
