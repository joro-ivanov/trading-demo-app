package com.example.demo.e2e.ui;

import org.junit.jupiter.api.BeforeEach;

import com.example.demo.e2e.framework.pages.LoginPage;
import com.example.demo.e2e.framework.pages.OrderTicketPage;
import com.example.demo.e2e.framework.pages.RegistrationPage;
import com.example.demo.e2e.framework.pages.DashboardPage;
import com.example.demo.e2e.framework.ui.PlaywrightManager;
import com.example.demo.e2e.BaseTest;

abstract class BaseUiTest extends BaseTest {

    @BeforeEach
    void openBaseUrl() {
        navigateToBaseUrl();
    }

    protected void navigateToBaseUrl() {
        PlaywrightManager.page().navigate(UI_BASE_URL);
    }

    protected void reopenPrimaryTabAtBaseUrl() {
        PlaywrightManager.reopenPrimaryPage(UI_BASE_URL);
    }

    protected LoginPage openLoginPage() {
        return new LoginPage(PlaywrightManager.page());
    }

    protected DashboardPage loginAsPrimaryUser() {
        return openLoginPage().login(username, REGISTERED_USER_PASSWORD);
    }

    protected DashboardPage loginAsUser(String username) {
        return openLoginPage().login(username, REGISTERED_USER_PASSWORD);
    }

    protected RegistrationPage openRegistrationPage() {
        return new RegistrationPage(PlaywrightManager.page()).switchToRegistrationForm();
    }

    protected DashboardPage dashboardPage() {
        return new DashboardPage(PlaywrightManager.page());
    }

    protected OrderTicketPage orderTicketPage() {
        return new OrderTicketPage(PlaywrightManager.page());
    }
}
