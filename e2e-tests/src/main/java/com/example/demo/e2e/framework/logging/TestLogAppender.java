package com.example.demo.e2e.framework.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Thread-local logback appender used to capture the full per-test log in a single buffer.
 */
public final class TestLogAppender extends AppenderBase<ILoggingEvent> {

    private static final ThreadLocal<StringBuilder> BUFFER = ThreadLocal.withInitial(StringBuilder::new);

    private String pattern;
    private PatternLayout layout;

    @Override
    public void start() {
        layout = new PatternLayout();
        layout.setContext(getContext());
        layout.setPattern(pattern == null || pattern.isBlank()
                ? "%d{yyyy-MM-dd HH:mm:ss.SSS z} [%thread] %-5level %logger - %msg%n"
                : pattern);
        layout.start();
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        BUFFER.get().append(layout.doLayout(eventObject));
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public static String dump() {
        return BUFFER.get().toString();
    }

    public static void clear() {
        BUFFER.remove();
    }
}
