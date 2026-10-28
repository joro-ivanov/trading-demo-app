package com.example.demo.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.pages.LoginPage;
import com.example.demo.e2e.framework.ui.LocalStorage;
import com.example.demo.e2e.framework.ui.SessionStorage;
import com.example.demo.e2e.harness.PlaywrightLifecycle;
import com.example.demo.e2e.harness.RequireRunningBackend;

import io.qameta.allure.Allure;

/**
 * Security e2e (UI): client-side token handling, session lifecycle, and output encoding in a real
 * browser. Not market-hours gated (no order placement).
 */
@Tag("ui")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(PlaywrightLifecycle.class)
class SecurityTest extends BaseUiTest {

    /** Storage key under which the dashboard would keep a session token. */
    private static final String SESSION_KEY = "session";

    @Test
    void refreshKeepsTheUserLoggedInUsingSessionStorageOnly() {
        DashboardPage dashboard = loginAsPrimaryUser();
        dashboard.awaitSessionUsername(username);
        Allure.step("The JWT is stored in sessionStorage only");
        dashboard.awaitTokenInSessionStorage();
        assertSoftly(softly -> {
            softly.assertThat(dashboard.tokenIsInSessionStorage()).isTrue();
            softly.assertThat(dashboard.tokenIsInLocalStorage()).isFalse();
        });

        Allure.step("Refresh the page");
        dashboard.refresh();
        dashboard.awaitSessionUsername(username);
        dashboard.awaitTokenInSessionStorage();
        assertSoftly(softly -> {
            softly.assertThat(dashboard.tokenIsInSessionStorage()).isTrue();
            softly.assertThat(dashboard.tokenIsInLocalStorage()).isFalse();
        });
    }

    @Test
    void closingTheTabAndOpeningANewOneReturnsToTheLoginScreen() {
        DashboardPage dashboard = loginAsPrimaryUser();
        dashboard.awaitSessionUsername(username);
        dashboard.awaitTokenInSessionStorage();

        Allure.step("Close the tab, open a new one in the same browser, and navigate to the app again");
        reopenPrimaryTabAtBaseUrl();

        LoginPage loginPage = openLoginPage();
        loginPage.awaitShown();
        assertSoftly(softly -> {
            softly.assertThat(LocalStorage.getItem(SESSION_KEY)).isNull();
            softly.assertThat(SessionStorage.getItem(SESSION_KEY)).isNull();
        });
    }

    @Test
    void loggingOutReturnsToTheLoginScreen() {
        DashboardPage dashboard = loginAsPrimaryUser();
        Allure.step("Log out");
        dashboard.logout();
        openLoginPage().awaitShown();
    }

    // A username containing markup is rendered as text (React escaping), not parsed into a live
    // element — a reflected/stored-XSS attempt does not execute.
    @Test
    void aMaliciousUsernameIsRenderedAsTextNotMarkup() {
        String payload = "<img src=x onerror=alert(1)>";
        LoginPage login = openLoginPage();
        login.stubLoginSuccess(payload);
        Allure.step("Log in with a username that contains an <img> XSS payload");
        login.fillCredentials(username, REGISTERED_USER_PASSWORD).submit();

        DashboardPage dashboard = dashboardPage();
        dashboard.awaitSessionUsername(payload);
        assertThat(dashboard.headerImageCount()).isEqualTo(0);
    }
}
