package com.example.demo.e2e.framework.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import com.example.demo.e2e.framework.http.Json;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.ui.BasePage;
import com.example.demo.e2e.framework.ui.RouteStub;

/**
 * Registration Page Object (reached from the login screen via the "Need an account?" toggle). Owns
 * the Playwright network interception of {@code POST /api/auth/register} for negative scenarios, plus
 * native-validity probes — the value over the jsdom unit tests is that a real browser enforces the
 * HTML5 rules. The tests use only these intent-revealing methods (ArchUnit §13.1).
 */
public final class RegistrationPage extends BasePage {

    private static final String REGISTER_TOGGLE_BUTTON = "Need an account? Register";
    private static final String REGISTER_ROUTE = "**/api/auth/register";
    private static final String STUB_TOKEN = "jwt";
    private static final String STUB_USERNAME = "newuser";
    private static final String USERNAME_INPUT = "input[name=username]";
    private static final String EMAIL_INPUT = "input[name=email]";
    private static final String PASSWORD_INPUT = "input[name=password]";
    private static final String CONFIRM_PASSWORD_INPUT = "input[name=confirmPassword]";
    private static final String SUBMIT = ".login button[type=submit]";
    private static final String ERROR = ".login .error";
    private static final String LOGOUT_BUTTON = "Log out";

    private final RouteStub registerRoute = intercept(REGISTER_ROUTE);

    public RegistrationPage(Page page) {
        super(page);
    }

    /** Switch from the login form to the registration form. */
    public RegistrationPage switchToRegistrationForm() {
        logUi("switch to registration form");
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(REGISTER_TOGGLE_BUTTON)).click();
        return this;
    }

    /** Fulfil every register POST with the given status and a valid login body. */
    public RegistrationPage stubRegister(int status) {
        logUi("stub register status=" + status);
        registerRoute.fulfill(status, Json.write(new Login(STUB_TOKEN, STUB_USERNAME)));
        return this;
    }

    public int registerRequestCount() {
        return registerRoute.count();
    }

    public RegistrationPage fill(String username, String email, String password, String confirm) {
        logUi("fill registration username=" + username + " email=" + email);
        page.locator(USERNAME_INPUT).fill(username);
        page.locator(EMAIL_INPUT).fill(email);
        page.locator(PASSWORD_INPUT).fill(password);
        page.locator(CONFIRM_PASSWORD_INPUT).fill(confirm);
        return this;
    }

    public RegistrationPage submit() {
        logUi("submit registration");
        page.locator(SUBMIT).click();
        return this;
    }

    public boolean emailIsValid() {
        return (boolean) page.locator(EMAIL_INPUT).evaluate("el => el.checkValidity()");
    }

    public boolean passwordIsValid() {
        return (boolean) page.locator(PASSWORD_INPUT).evaluate("el => el.checkValidity()");
    }

    public boolean usernameIsValid() {
        return (boolean) page.locator(USERNAME_INPUT).evaluate("el => el.checkValidity()");
    }

    public boolean submitDisabled() {
        return page.locator(SUBMIT).isDisabled();
    }

    public RegistrationPage awaitRegisterError(String text) {
        logUi("await register error text=" + text);
        waitForText(page.locator(ERROR), text);
        return this;
    }

    /** After a successful registration the app logs in and shows the dashboard ("Log out" appears). */
    public void awaitDashboard() {
        logUi("await dashboard after registration");
        waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGOUT_BUTTON)));
    }
}
