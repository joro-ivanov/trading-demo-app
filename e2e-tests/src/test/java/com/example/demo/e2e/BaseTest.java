package com.example.demo.e2e;

import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.Position;
import com.example.demo.e2e.framework.service.auth.AuthService;
import com.example.demo.e2e.framework.service.funds.FundsService;
import com.example.demo.e2e.framework.service.portfolio.PortfolioService;
import com.example.demo.e2e.framework.support.Lookup;
import com.example.demo.e2e.framework.support.Tokens;
import com.example.demo.e2e.framework.ui.PlaywrightManager;

public abstract class BaseTest {

    protected static final String REGISTERED_USER_PASSWORD = "s3cret-pw";
    protected static final String EMAIL_DOMAIN = "demo.example.com";
    /** Default prefix of generated usernames (a random suffix keeps every user unique). */
    protected static final String USERNAME_PREFIX = "u";
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
    /** Quantity of the main order in most trading scenarios (API and dashboard). */
    protected static final int ORDER_QUANTITY = 10;
    protected static final BigDecimal ORDER_SIZE = BigDecimal.valueOf(ORDER_QUANTITY);
    /** Position bought first so that a later SELL passes the short-sell guard. */
    protected static final BigDecimal SEEDED_POSITION = new BigDecimal("20");
    /** Below any market price: a SELL limit or BUY stop at it is marketable, a BUY limit at it rests. */
    protected static final BigDecimal BELOW_MARKET_PRICE = new BigDecimal("0.01");
    /** 10x the demo prices: never reached by the feed (non-marketable), notional within risk limits. */
    protected static final BigDecimal UNREACHABLE_STOP_PRICE = new BigDecimal("1000");
    /** Fills of a scenario that buys and then sells once. */
    protected static final int BUY_AND_SELL_FILLS = 2;
    private static final String UUID_SEPARATOR = "-";
    private static final int USERNAME_SUFFIX_LENGTH = 10;
    private static final String EMAIL_AT = "@";
    protected String username;
    protected String token;

    protected static String uniqueUsername() {
        return uniqueUsername(USERNAME_PREFIX);
    }

    protected static String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace(UUID_SEPARATOR, "").substring(0, USERNAME_SUFFIX_LENGTH);
    }

    protected static String emailFor(String username) {
        return username + EMAIL_AT + EMAIL_DOMAIN;
    }

    @BeforeEach
    void registerPrimaryUser() {
        Login primaryUser = registerFreshUser(USERNAME_PREFIX);
        username = primaryUser.username();
        token = primaryUser.token();
        FundsService.deposit(token, DEFAULT_STARTING_CASH);
    }

    protected Login registerFreshUser(String prefix) {
        String freshUsername = uniqueUsername(prefix);
        ApiResponse response = AuthService.register(freshUsername, emailFor(freshUsername), REGISTERED_USER_PASSWORD);
        Login loginView = response.as(Login.class);
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(CREATED);
            softly.assertThat(loginView.username()).isEqualTo(freshUsername);
            softly.assertThat(Tokens.claims(loginView.token()).sub()).isEqualTo(freshUsername);
            softly.assertThat(Tokens.claims(loginView.token()).iss()).isEqualTo(Tokens.AUTH_SERVICE_ISSUER);
        });
        return loginView;
    }

    protected String registerFreshUserToken(String prefix) {
        return registerFreshUser(prefix).token();
    }

    /** The primary user's position in {@code symbol} (fails if there is none). */
    protected Position position(String symbol) {
        return Lookup.position(PortfolioService.positions(token).asList(Position.class), symbol);
    }
}
