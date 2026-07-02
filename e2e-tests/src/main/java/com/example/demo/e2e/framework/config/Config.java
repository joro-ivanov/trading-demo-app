package com.example.demo.e2e.framework.config;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Singleton configuration with strict resolution precedence (docs/DESIGN.md §11.1):
 *
 * <pre>
 *   1. -D system property        e.g. -Dui.browser.headless=false
 *   2. environment variable      e.g. UI_BROWSER_HEADLESS=false  (key uppercased, '.'/'-' -> '_')
 *   3. application-&lt;env&gt;.properties   env selected by the `env` property, default `local`
 * </pre>
 *
 * The selected properties file is loaded from the classpath as
 * {@code application-<env>.properties}.
 */
public final class Config {

    private static final Config INSTANCE = new Config();

    private final Properties fileProps = new Properties();
    private final String env;

    private Config() {
        this.env = resolve("env", "local");
        String resource = "application-" + env + ".properties";
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                fileProps.load(in);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config resource: " + resource, e);
        }
    }

    public static Config get() {
        return INSTANCE;
    }

    public String env() {
        return env;
    }

    /** Resolve a property, or {@code null} if absent in all three sources. */
    public String get(String key) {
        return resolve(key, fileProps.getProperty(key));
    }

    /** Resolve a property, falling back to {@code defaultValue} when absent. */
    public String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, Boolean.toString(defaultValue)));
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, Integer.toString(defaultValue)));
    }

    public Duration getDuration(String key, Duration defaultValue) {
        String value = get(key);
        return value != null ? parseDuration(value) : defaultValue;
    }

    // Convenience accessors for the most-used keys.
    public String baseUrl() {
        return get("base.url");
    }

    public String wsBaseUrl() {
        return get("ws.base.url");
    }

    private static String resolve(String key, String fileValue) {
        String system = System.getProperty(key);
        if (system != null) {
            return system;
        }
        String environment = System.getenv(toEnvVar(key));
        if (environment != null) {
            return environment;
        }
        return fileValue;
    }

    private static String toEnvVar(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static Duration parseDuration(String raw) {
        String value = raw.trim().toLowerCase();
        if (value.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.parse(raw);
    }
}
