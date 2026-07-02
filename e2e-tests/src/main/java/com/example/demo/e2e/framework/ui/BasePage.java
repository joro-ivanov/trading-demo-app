package com.example.demo.e2e.framework.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.time.Duration;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.example.demo.e2e.framework.config.Config;

import com.example.demo.e2e.framework.logging.TestLog;

/**
 * Base for all Page Objects. Constructors receive the current {@link Page} explicitly (docs/DESIGN.md
 * §8.6, enforced by ArchUnit §13.1), which keeps page objects composable and avoids hidden global UI
 * state. Subclasses expose intent-revealing action/query methods; the e2e tests use only those
 * methods (never the Playwright API directly).
 */
public abstract class BasePage {

    private static final long UI_WAIT_TIMEOUT_MILLIS = Config.get()
            .getDuration("ui.playwright.default-timeout", Duration.ofSeconds(10))
            .toMillis();

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    protected final void logUi(String message) {
        TestLog.log("UI " + message);
    }

    /**
     * A counting request stub for the given URL glob (e.g. {@code "**}{@code /api/auth/login"}). Keeps
     * Playwright network interception out of the individual Page Objects — they expose domain-named
     * methods that delegate to the returned {@link RouteStub}.
     */
    protected final RouteStub intercept(String glob) {
        return new RouteStub(page, glob);
    }

    protected final void waitForVisible(Locator locator) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForHidden(Locator locator) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForText(Locator locator, String text) {
        assertThat(locator.first()).hasText(text,
                new LocatorAssertions.HasTextOptions().setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForTextContains(Locator locator, String text) {
        assertThat(locator.first()).containsText(text,
                new LocatorAssertions.ContainsTextOptions().setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForCount(Locator locator, int count) {
        assertThat(locator).hasCount(count,
                new LocatorAssertions.HasCountOptions().setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForEnabled(Locator locator) {
        assertThat(locator.first()).isEnabled(
                new LocatorAssertions.IsEnabledOptions().setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }

    protected final void waitForDisabled(Locator locator) {
        assertThat(locator.first()).isDisabled(
                new LocatorAssertions.IsDisabledOptions().setTimeout(UI_WAIT_TIMEOUT_MILLIS));
    }
}
