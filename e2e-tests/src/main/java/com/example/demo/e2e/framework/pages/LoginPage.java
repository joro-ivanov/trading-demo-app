package com.example.demo.e2e.framework.pages;

import java.util.Map;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import com.example.demo.common.json.Json;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.ui.BasePage;
import com.example.demo.e2e.framework.ui.RouteStub;

/**
 * Login screen Page Object. Besides the happy-path {@code login}, it owns network interception of
 * the login POST for negative scenarios (mirrors OrderTicketPage).
 */
public final class LoginPage extends BasePage {

    private static final String USERNAME_LABEL = "Username";
    private static final String PASSWORD_LABEL = "Password";
    private static final String ENTER_BUTTON = "Enter";
    private static final String ERROR = ".login .error";
    private static final String LOGIN_ROUTE = "**/api/auth/login";
    private static final String STUB_TOKEN = "stub-token";
    private static final int STUB_OK = 200;
    private static final String EMPTY_JSON_OBJECT = Json.write(Map.of());

    private final RouteStub loginRoute = intercept(LOGIN_ROUTE);

    public LoginPage(Page page) {
        super(page);
    }

    public DashboardPage login(String username, String password) {
        logUi("login username=" + username);
        page.getByLabel(USERNAME_LABEL).fill(username);
        page.getByLabel(PASSWORD_LABEL).fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ENTER_BUTTON)).click();
        return new DashboardPage(page);
    }

    // ── negative scenarios (network interception + step-wise submit) ──────────

    /** Fulfil every login POST with the given HTTP status (and an empty JSON body). */
    public LoginPage stubLogin(int status) {
        logUi("stub login status=" + status);
        loginRoute.fulfill(status, EMPTY_JSON_OBJECT);
        return this;
    }

    /** Fulfil the login POST with a 200 carrying the given username (for output-encoding tests). */
    public LoginPage stubLoginSuccess(String username) {
        logUi("stub login success username=" + username);
        loginRoute.fulfill(STUB_OK, Json.write(new Login(STUB_TOKEN, username)));
        return this;
    }

    /** The login form is visible (e.g. after logout). */
    public LoginPage awaitShown() {
        logUi("await login page shown");
        waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ENTER_BUTTON)));
        return this;
    }

    public int loginRequestCount() {
        return loginRoute.count();
    }

    public LoginPage fillCredentials(String username, String password) {
        logUi("fill login credentials username=" + username);
        page.getByLabel(USERNAME_LABEL).fill(username);
        page.getByLabel(PASSWORD_LABEL).fill(password);
        return this;
    }

    public LoginPage submit() {
        logUi("submit login");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ENTER_BUTTON)).click();
        return this;
    }

    public LoginPage awaitLoginError() {
        logUi("await login error");
        waitForText(page.locator(ERROR), "Login failed — try demo / password");
        return this;
    }
}
