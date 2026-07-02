package com.example.demo.e2e.framework.allure;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.example.demo.e2e.framework.logging.TestLog;

import io.qameta.allure.Allure;

/** JUnit extension and shared policy for attaching the per-test log buffer to Allure. */
public final class TestLogArtifacts implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        clear();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        attachOnFailure(context);
        clear();
    }

    public static void clear() {
        TestLog.clear();
    }

    public static void attachOnFailure(ExtensionContext context) {
        String log = TestLog.dump();
        if (context.getExecutionException().isPresent() && !log.isBlank()) {
            Allure.addAttachment("Full test log", "text/plain", log, ".txt");
        }
    }
}
