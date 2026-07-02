package com.example.demo.e2e;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.service.auth.AuthService;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.ui.PlaywrightManager;

public abstract class BaseTest {

    protected static final String REGISTERED_USER_PASSWORD = "s3cret-pw";
    protected static final String EMAIL_DOMAIN = "demo.example.com";
    protected static final BigDecimal DEFAULT_STARTING_CASH = new BigDecimal("10000");
    protected static final String UI_BASE_URL = PlaywrightManager.browserReachableUiUrl(Config.get().get("ui.base.url"));
    protected static final Duration AWAIT_TIMEOUT = Config.get()
            .getDuration("e2e.await.timeout", Duration.ofSeconds(10));
    protected static final Duration SHORT_AWAIT_TIMEOUT = Config.get()
            .getDuration("e2e.await.short-timeout", Duration.ofSeconds(8));
    protected static final Duration STABILITY_TIMEOUT = Config.get()
            .getDuration("e2e.await.stability-timeout", Duration.ofSeconds(3));
    protected static final Duration STABILITY_POLL_DELAY = Config.get()
            .getDuration("e2e.await.stability-poll-delay", Duration.ofSeconds(1));
    protected static final Duration WS_FRAME_TIMEOUT = Config.get()
            .getDuration("e2e.ws.frame-timeout", Duration.ofSeconds(10));
    protected static final Duration SHORT_WS_FRAME_TIMEOUT = Config.get()
            .getDuration("e2e.ws.frame.short-timeout", Duration.ofSeconds(8));
    protected String username;
    protected String token;

    protected static String uniqueUsername() {
        return uniqueUsername("u");
    }

    protected static String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected static String emailFor(String username) {
        return username + "@" + EMAIL_DOMAIN;
    }

    @BeforeEach
    void registerPrimaryUser() {
        Login primaryUser = registerFreshUser("u");
        username = primaryUser.username();
        token = primaryUser.token();
        FundsService.deposit(token, DEFAULT_STARTING_CASH);
    }

    protected Login registerFreshUser(String prefix) {
        String freshUsername = uniqueUsername(prefix);
        ApiResponse response = AuthService.register(freshUsername, emailFor(freshUsername), REGISTERED_USER_PASSWORD);
        Login loginView = response.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(loginView.username()).isEqualTo(freshUsername);
            softly.assertThat(loginView.token()).isNotBlank();
        });
        return loginView;
    }

    protected String registerFreshUserToken(String prefix) {
        return registerFreshUser(prefix).token();
    }
}
