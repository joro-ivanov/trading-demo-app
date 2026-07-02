package com.example.demo.e2e.framework.ui;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.example.demo.e2e.framework.config.Config;

/**
 * A counting request stub for a URL glob, used by Page Objects to make negative UI scenarios
 * deterministic: a matching request can be fulfilled with any status, aborted (backend down) or hung
 * (no response), and the number of attempts is recorded. Created via {@link BasePage#intercept(String)}
 * so the Playwright {@code Route} usage stays confined to the ui layer (ArchUnit §13.1).
 */
public final class RouteStub {

    private static final Duration HUNG_RESPONSE_DELAY = Config.get()
            .getDuration("ui.stub.hung-response-delay", Duration.ofSeconds(5));
    private static final ScheduledExecutorService HUNG_RESPONSE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private final Page page;
    private final String pattern;
    private final AtomicInteger count = new AtomicInteger();

    // Package-private: only BasePage#intercept constructs these.
    RouteStub(Page page, String pattern) {
        this.page = page;
        this.pattern = pattern;
    }

    /** Fulfil every matching request with the given status and JSON body (the backend is never hit). */
    public void fulfill(int status, String body) {
        page.route(pattern, route -> {
            count.incrementAndGet();
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(status).setContentType("application/json").setBody(body));
        });
    }

    /** Abort every matching request at the transport layer (simulates the backend being down). */
    public void abort() {
        page.route(pattern, route -> {
            count.incrementAndGet();
            route.abort();
        });
    }

    /**
     * Keep every matching request in flight for a while, then fail it. This preserves the
     * observable "hung backend" UI state without leaving a permanently open request that can block
     * Playwright shutdown during test teardown.
     */
    public void hang() {
        page.route(pattern, route -> {
            count.incrementAndGet();
            HUNG_RESPONSE_EXECUTOR.schedule(() -> abortQuietly(route),
                    HUNG_RESPONSE_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    public int count() {
        return count.get();
    }

    private static void abortQuietly(Route route) {
        try {
            route.abort("timedout");
        } catch (RuntimeException ignored) {
            // The page/context may already be gone by the time the delayed abort fires.
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "route-stub-hang");
            thread.setDaemon(true);
            return thread;
        }
    }
}
