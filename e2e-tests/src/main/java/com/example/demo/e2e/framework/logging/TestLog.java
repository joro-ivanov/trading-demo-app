package com.example.demo.e2e.framework.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade over a dedicated SLF4J logger whose events are harvested by {@link TestLogAppender}.
 */
public final class TestLog {

    private static final Logger LOG = LoggerFactory.getLogger("com.example.demo.e2e.testlog");

    private TestLog() {
    }

    public static void log(String message) {
        LOG.info("{}", message);
    }

    public static String dump() {
        return TestLogAppender.dump();
    }

    public static void clear() {
        TestLogAppender.clear();
    }
}
