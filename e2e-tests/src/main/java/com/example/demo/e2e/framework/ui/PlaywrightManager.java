package com.example.demo.e2e.framework.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Properties;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RecordVideoSize;
import org.testcontainers.Testcontainers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.Json;

/**
 * Thread-local Playwright lifecycle owner. UI test fixtures pass the current {@link Page} from here
 * into Page Object constructors (docs/DESIGN.md §8.6, §13.1).
 *
 * <p>Most tests use a single context. A test that needs two simultaneous users (e.g. per-user
 * portfolio isolation) calls {@link #openSecondContext()} to spin up a second, isolated browser
 * context and switch the current page to it; {@link #useFirstContext()} / {@link #useSecondContext()}
 * toggle which one new Page Objects bind to. A Page Object captures its page at construction, so
 * objects built before/after the switch each drive their own context. Failure artifacts (trace,
 * screenshot, video, console) are always taken from the <b>primary</b> context.
 *
 * <p>Honours the UI config (docs/DESIGN.md §7.4, §11.1):
 * <ul>
 *   <li>{@code ui.browser} — chromium | firefox | webkit</li>
 *   <li>{@code ui.browser.headless} — true | false</li>
 *   <li>{@code ui.browser.channel} — a real installed browser (e.g. chrome, msedge), or blank for the bundled engine</li>
 *   <li>{@code ui.browser.mode} — local | testcontainers (connect to a Playwright server, no host browser)</li>
 *   <li>{@code ui.video} — on | retain-on-failure | off</li>
 * </ul>
 */
public final class PlaywrightManager {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightManager.class);
    private static final String PLAYWRIGHT_VERSION = loadPlaywrightVersion();
    private static final String TESTCONTAINERS_HOST = "host.testcontainers.internal";

    private static final DockerImageName PLAYWRIGHT_IMAGE =
            DockerImageName.parse("mcr.microsoft.com/playwright:v" + PLAYWRIGHT_VERSION + "-noble");
    private static final int PLAYWRIGHT_SERVER_PORT = 3000;
    private static final String PLAYWRIGHT_WS_PATH = "/playwright";
    private static final String PLAYWRIGHT_SERVER_HOST = "0.0.0.0";

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<GenericContainer<?>> BROWSER_CONTAINER = new ThreadLocal<>();
    private static final ConcurrentMap<String, SharedBrowser> SHARED_BROWSERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> SECOND_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> SECOND_PAGE = new ThreadLocal<>();
    // What page()/context() return — the page new Page Objects bind to (defaults to the primary).
    private static final ThreadLocal<Page> CURRENT_PAGE = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private PlaywrightManager() {
    }

    public static Page page() {
        return CURRENT_PAGE.get();
    }

    public static BrowserContext context() {
        return CURRENT_CONTEXT.get();
    }

    /** The primary page/context — used for failure artifacts regardless of the current selection. */
    public static Page primaryPage() {
        return PAGE.get();
    }

    public static BrowserContext primaryContext() {
        return CONTEXT.get();
    }

    public static void startClass(String classKey) {
        SHARED_BROWSERS.computeIfAbsent(classKey, ignored -> createSharedBrowser(Config.get()));
    }

    public static void stopClass(String classKey) {
        SharedBrowser shared = SHARED_BROWSERS.remove(classKey);
        if (shared == null) {
            return;
        }
        LOG.info("Stopping Playwright browser resources for class scope {}", classKey);
        shared.browser().close();
        if (shared.browserContainer() != null) {
            shared.browserContainer().stop();
        }
        shared.playwright().close();
    }

    public static void startTest(String classKey) {
        Config config = Config.get();
        SharedBrowser shared = SHARED_BROWSERS.get(classKey);
        if (shared == null) {
            throw new IllegalStateException("No class-scoped Playwright browser started for " + classKey);
        }

        PLAYWRIGHT.set(shared.playwright());
        BROWSER.set(shared.browser());
        BROWSER_CONTAINER.set(shared.browserContainer());

        BrowserContext context = shared.browser().newContext(contextOptions(config));
        Page page = context.newPage();
        double defaultTimeoutMillis = config
                .getDuration("ui.playwright.default-timeout", Duration.ofSeconds(10))
                .toMillis();
        double navigationTimeoutMillis = config
                .getDuration("ui.playwright.navigation-timeout", Duration.ofSeconds(10))
                .toMillis();
        context.setDefaultTimeout(defaultTimeoutMillis);
        context.setDefaultNavigationTimeout(navigationTimeoutMillis);
        page.setDefaultTimeout(defaultTimeoutMillis);
        page.setDefaultNavigationTimeout(navigationTimeoutMillis);

        CONTEXT.set(context);
        PAGE.set(page);
        CURRENT_CONTEXT.set(context);
        CURRENT_PAGE.set(page);
    }

    /** Open a second, isolated browser context (a second user) and make it the current selection. */
    public static void openSecondContext() {
        BrowserContext context = BROWSER.get().newContext(contextOptions(Config.get()));
        Page page = context.newPage();
        SECOND_CONTEXT.set(context);
        SECOND_PAGE.set(page);
        CURRENT_CONTEXT.set(context);
        CURRENT_PAGE.set(page);
    }

    public static void useFirstContext() {
        CURRENT_CONTEXT.set(CONTEXT.get());
        CURRENT_PAGE.set(PAGE.get());
    }

    public static void useSecondContext() {
        CURRENT_CONTEXT.set(SECOND_CONTEXT.get());
        CURRENT_PAGE.set(SECOND_PAGE.get());
    }

    /**
     * Simulate closing the primary tab and opening a fresh one in the same browser context, then
     * navigate it to the given URL. This preserves browser-context state (cookies/localStorage)
     * while clearing tab-scoped state such as sessionStorage.
     */
    public static void reopenPrimaryPage(String url) {
        Page previous = PAGE.get();
        if (previous != null) {
            previous.close();
        }
        Page reopened = CONTEXT.get().newPage();
        reopened.navigate(url);
        PAGE.set(reopened);
        CURRENT_CONTEXT.set(CONTEXT.get());
        CURRENT_PAGE.set(reopened);
    }

    public static void closeContexts() {
        close(SECOND_PAGE.get(), SECOND_CONTEXT.get());
        close(PAGE.get(), CONTEXT.get());
        CURRENT_PAGE.remove();
        CURRENT_CONTEXT.remove();
        SECOND_PAGE.remove();
        SECOND_CONTEXT.remove();
        PAGE.remove();
        CONTEXT.remove();
        BROWSER.remove();
        BROWSER_CONTAINER.remove();
        PLAYWRIGHT.remove();
    }

    public static String browserReachableUiUrl(String url) {
        if (!"testcontainers".equals(Config.get().get("ui.browser.mode", "local"))) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            if (!isHostLocal(uri.getHost())) {
                return url;
            }
            String rewritten = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    TESTCONTAINERS_HOST,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment())
                    .toString();
            LOG.info("Rewriting UI URL for Testcontainers browser: {} -> {}", url, rewritten);
            return rewritten;
        } catch (RuntimeException | java.net.URISyntaxException e) {
            LOG.warn("Failed to rewrite UI URL for Testcontainers browser, using original: {}", url, e);
            return url;
        }
    }

    private static void close(Page page, BrowserContext context) {
        if (page != null) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
    }

    private static Browser.NewContextOptions contextOptions(Config config) {
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        int viewportWidth = config.getInt("ui.viewport.width", 1920);
        int viewportHeight = config.getInt("ui.viewport.height", 1080);
        options.setViewportSize(viewportWidth, viewportHeight);
        if (!"off".equals(config.get("ui.video", "retain-on-failure"))) {
            options.setRecordVideoDir(videoDir());
            options.setRecordVideoSize(new RecordVideoSize(
                    config.getInt("ui.video.width", viewportWidth),
                    config.getInt("ui.video.height", viewportHeight)));
        }
        return options;
    }

    private static SharedBrowser createSharedBrowser(Config config) {
        Playwright playwright = Playwright.create();
        String browserName = config.get("ui.browser", "chromium");
        BrowserType browserType = browserType(playwright, browserName);
        LOG.info("Starting Playwright browser: mode={}, browser={}, headless={}, channel={}",
                config.get("ui.browser.mode", "local"),
                browserName,
                config.getBoolean("ui.browser.headless", true),
                blankAsDefault(config.get("ui.browser.channel", "")));
        LaunchResult launch = launch(browserType, config);
        return new SharedBrowser(playwright, launch.browser(), launch.browserContainer());
    }

    private static LaunchResult launch(BrowserType browserType, Config config) {
        boolean testcontainers = "testcontainers".equals(config.get("ui.browser.mode", "local"));
        if (testcontainers) {
            GenericContainer<?> browserContainer = browserContainer(config);
            browserContainer.start();
            LOG.info("Playwright container logs after startup:\n{}", browserContainer.getLogs());
            String wsEndpoint = "ws://"
                    + browserContainer.getHost() + ":"
                    + browserContainer.getMappedPort(PLAYWRIGHT_SERVER_PORT) + PLAYWRIGHT_WS_PATH;
            LOG.info("Playwright Testcontainers browser started: image={}, containerId={}, wsEndpoint={}",
                    PLAYWRIGHT_IMAGE, browserContainer.getContainerId(), wsEndpoint);
            try {
                return new LaunchResult(browserType.connect(wsEndpoint), browserContainer);
            } catch (RuntimeException e) {
                LOG.error("Playwright browser connect failed for {}. Container logs:\n{}",
                        wsEndpoint, browserContainer.getLogs(), e);
                throw e;
            }
        }
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(config.getBoolean("ui.browser.headless", true));
        String channel = config.get("ui.browser.channel", "");
        if (!channel.isBlank()) {
            options.setChannel(channel);
        }
        LOG.info("Launching local Playwright browser: browser={}, headless={}, channel={}",
                config.get("ui.browser", "chromium"),
                config.getBoolean("ui.browser.headless", true),
                blankAsDefault(channel));
        return new LaunchResult(browserType.launch(options), null);
    }

    private static GenericContainer<?> browserContainer(Config config) {
        exposeHostUiPort(config);
        String browser = config.get("ui.browser", "chromium");
        String launchConfig = Json.write(new LaunchServerConfig(
                PLAYWRIGHT_SERVER_PORT, PLAYWRIGHT_SERVER_HOST, PLAYWRIGHT_WS_PATH, config.getBoolean("ui.browser.headless", true)));
        String command = "printf '%s' '" + launchConfig + "' >/tmp/playwright-launch.json"
                + " && npx -y playwright@" + PLAYWRIGHT_VERSION + " launch-server --browser " + browser
                + " --config /tmp/playwright-launch.json";
        Duration startupTimeout = config.getDuration("ui.testcontainers.startup-timeout", Duration.ofSeconds(30));
        return new GenericContainer<>(PLAYWRIGHT_IMAGE)
                .withExposedPorts(PLAYWRIGHT_SERVER_PORT)
                .withAccessToHost(true)
                .withCommand("sh", "-c", command)
                .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("playwright-container"))
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forListeningPort())
                        .withStrategy(Wait.forLogMessage(".*ws://0\\.0\\.0\\.0:3000/playwright.*\\n", 1))
                        .withStartupTimeout(startupTimeout));
    }

    private static BrowserType browserType(Playwright playwright, String name) {
        return switch (name) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
    }

    private static Path videoDir() {
        return Paths.get("target", "playwright-videos");
    }

    private static String blankAsDefault(String value) {
        return value == null || value.isBlank() ? "<bundled>" : value;
    }

    private static void exposeHostUiPort(Config config) {
        try {
            URI uri = URI.create(config.get("ui.base.url"));
            if (isHostLocal(uri.getHost()) && uri.getPort() > 0) {
                Testcontainers.exposeHostPorts(uri.getPort());
            }
        } catch (RuntimeException e) {
            LOG.warn("Failed to expose host UI port for Testcontainers browser", e);
        }
    }

    private static boolean isHostLocal(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private static String loadPlaywrightVersion() {
        Properties properties = new Properties();
        try (var in = PlaywrightManager.class.getClassLoader().getResourceAsStream("playwright.properties")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: playwright.properties");
            }
            properties.load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Playwright version from playwright.properties", e);
        }
        String version = properties.getProperty("playwright.version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("playwright.version is missing from playwright.properties");
        }
        return version.trim();
    }

    /** {@code playwright launch-server --config} file contents. */
    private record LaunchServerConfig(int port, String host, String wsPath, boolean headless) {
    }

    private record LaunchResult(Browser browser, GenericContainer<?> browserContainer) {
    }

    private record SharedBrowser(Playwright playwright, Browser browser, GenericContainer<?> browserContainer) {
    }
}
