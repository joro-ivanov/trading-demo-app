package com.example.demo.e2e.harness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.ui.PlaywrightManager;

import io.qameta.allure.Allure;

/**
 * Manages the Playwright browser for UI e2e tests and harvests rich failure artifacts into Allure
 * (docs/DESIGN.md §11.2): screenshot, browser console log, Playwright trace (zip, viewable in
 * Allure), and the session video. Skips the test if the dashboard stack is not running (start it
 * with {@code docker compose up}). Browser mode (local vs Testcontainers, headless, channel) comes
 * from {@code Config} via {@link PlaywrightManager}.
 */
public final class PlaywrightLifecycle implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightLifecycle.class);
    private static final ConcurrentMap<String, Long> START_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, List<String>> CONSOLE = new ConcurrentHashMap<>();

    @Override
    public void beforeAll(ExtensionContext context) {
        String url = Config.get().get("ui.base.url");
        Assumptions.assumeTrue(Reachability.getsOk(url), "UI stack not reachable at " + url + " — run: docker compose up");
        PlaywrightManager.startClass(classKey(context));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = testName(context);
        START_TIMES.put(context.getUniqueId(), System.nanoTime());
        LOG.info("UI test started: {}", testName);
        TestLogArtifacts.clear();

        PlaywrightManager.startTest(classKey(context));
        List<String> console = Collections.synchronizedList(new ArrayList<>());
        CONSOLE.put(context.getUniqueId(), console);
        PlaywrightManager.primaryPage()
                .onConsoleMessage(message -> console.add(message.type() + ": " + message.text()));
        PlaywrightManager.primaryContext().tracing().start(
                new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Page page = PlaywrightManager.primaryPage();
        boolean failed = context.getExecutionException().isPresent();
        logFinish(context, failed);
        TestLogArtifacts.attachOnFailure(context);
        if (page == null) {
            TestLogArtifacts.clear();
            return;
        }

        if (failed) {
            Allure.addAttachment("screenshot", "image/png", new ByteArrayInputStream(page.screenshot()), ".png");
            Allure.addAttachment("console", "text/plain",
                    String.join(System.lineSeparator(), CONSOLE.getOrDefault(context.getUniqueId(), List.of())), ".txt");
        }

        Path tracePath = Path.of("target", "playwright-traces", "trace-" + System.nanoTime() + ".zip");
        PlaywrightManager.primaryContext().tracing().stop(new Tracing.StopOptions().setPath(tracePath));
        Video video = failed ? page.video() : null;
        PlaywrightManager.closeContexts();
        Path videoPath = failed ? videoPath(video) : null;

        if (failed) {
            attach("trace.zip", "application/zip", tracePath);
            if (videoPath != null) {
                attach("video.webm", "video/webm", videoPath);
            } else {
                LOG.warn("No Playwright video was attached for failed test {}", testName(context));
            }
        }
        CONSOLE.remove(context.getUniqueId());
        TestLogArtifacts.clear();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        PlaywrightManager.stopClass(classKey(context));
    }

    private static void attach(String name, String type, Path path) {
        try {
            Allure.addAttachment(name, type, new ByteArrayInputStream(Files.readAllBytes(path)),
                    name.substring(name.lastIndexOf('.')));
            LOG.info("Attached failure artifact: {}", path);
        } catch (IOException e) {
            LOG.warn("Failed to attach artifact {}", path, e);
        }
    }

    private static Path videoPath(Video video) {
        if (video == null) {
            LOG.warn("Playwright page video was null");
            return null;
        }
        try {
            Path path = Path.of("target", "playwright-videos", "video-" + System.nanoTime() + ".webm");
            Files.createDirectories(path.getParent());
            video.saveAs(path);
            LOG.info("Saved Playwright video to {}", path);
            return path;
        } catch (RuntimeException | IOException e) {
            LOG.warn("Failed to save Playwright video", e);
            return null;
        }
    }

    private static void logFinish(ExtensionContext context, boolean failed) {
        Long startedAt = START_TIMES.remove(context.getUniqueId());
        long durationMs = startedAt == null ? -1 : (System.nanoTime() - startedAt) / 1_000_000;
        String testName = testName(context);
        if (durationMs >= 0) {
            LOG.info("UI test finished: {} [{}] ({} ms)", testName, failed ? "FAILED" : "PASSED", durationMs);
        } else {
            LOG.info("UI test finished: {} [{}]", testName, failed ? "FAILED" : "PASSED");
        }
    }

    private static String testName(ExtensionContext context) {
        return context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
    }

    private static String classKey(ExtensionContext context) {
        return context.getRequiredTestClass().getName();
    }
}
