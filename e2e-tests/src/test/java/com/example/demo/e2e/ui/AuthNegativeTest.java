package com.example.demo.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.demo.e2e.framework.pages.LoginPage;
import com.example.demo.e2e.framework.pages.RegistrationPage;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;

import io.qameta.allure.Allure;

/**
 * Auth UI e2e: negative login + registration field validation, in a real browser. Network
 * interception ({@link RegistrationPage}/{@link LoginPage}) makes the failure scenarios
 * deterministic; the happy-path registration runs against the live stack. Not market-hours gated
 * (auth is not order placement).
 */
@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class AuthNegativeTest extends BaseUiTest {

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void rejectedLoginShowsAnErrorAndDoesNotEnterTheApp() {
        LoginPage login = openLoginPage();
        Allure.step("The login POST is rejected with 401");
        login.stubLogin(401);
        login.fillCredentials(username, "wrong-password").submit();

        login.awaitLoginError();
        assertThat(login.loginRequestCount()).isEqualTo(1);
    }

    // ── registration field validation (real browser enforces HTML5 rules) ─────

    @Test
    void invalidEmailIsBlockedByNativeValidationAndSendsNoRequest() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(201);
        Allure.step("Fill a syntactically invalid email and submit");
        register.fill("validuser", "not-an-email", REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD).submit();

        assertSoftly(softly -> {
            softly.assertThat(register.emailIsValid()).isFalse();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void shortPasswordIsBlockedByNativeValidation() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(201);
        Allure.step("A password under the 8-char minimum is invalid and submits nothing");
        register.fill("validuser", "valid@" + EMAIL_DOMAIN, "short", "short").submit();

        assertSoftly(softly -> {
            softly.assertThat(register.passwordIsValid()).isFalse();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void confirmMismatchDisablesSubmitAndShowsAnError() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(201);
        Allure.step("Confirm password differs from password");
        register.fill("validuser", "valid@" + EMAIL_DOMAIN, REGISTERED_USER_PASSWORD, "different");

        register.awaitRegisterError("Passwords do not match.");
        assertSoftly(softly -> {
            softly.assertThat(register.submitDisabled()).isTrue();
            softly.assertThat(register.registerRequestCount()).isEqualTo(0);
        });
    }

    @Test
    void duplicateUsernameShowsAConflictMessage() {
        RegistrationPage register = openRegistrationPage();
        register.stubRegister(409);
        Allure.step("The register POST returns 409");
        register.fill(username, username + ".new@" + EMAIL_DOMAIN, REGISTERED_USER_PASSWORD, REGISTERED_USER_PASSWORD)
                .submit();

        register.awaitRegisterError("That username is already taken.");
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
