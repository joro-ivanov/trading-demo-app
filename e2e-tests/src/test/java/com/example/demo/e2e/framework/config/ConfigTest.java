package com.example.demo.e2e.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigTest {

    private static final String USERNAME_KEY = "auth.username";
    /** The value of {@link #USERNAME_KEY} in the e2e properties file. */
    private static final String FILE_USERNAME = "demo";
    private static final String OVERRIDDEN_USERNAME = "overridden";
    private static final String ABSENT_KEY = "does.not.exist";
    private static final String FALLBACK = "fallback";

    @Test
    void readsValueFromPropertiesFile() {
        assertThat(Config.get().get(USERNAME_KEY)).isEqualTo(FILE_USERNAME);
    }

    @Test
    void systemPropertyOverridesFileValue() {
        System.setProperty(USERNAME_KEY, OVERRIDDEN_USERNAME);

        String resolved = Config.get().get(USERNAME_KEY);
        System.clearProperty(USERNAME_KEY);

        assertThat(resolved).isEqualTo(OVERRIDDEN_USERNAME);
    }

    @Test
    void defaultUsedWhenKeyAbsentEverywhere() {
        assertThat(Config.get().get(ABSENT_KEY, FALLBACK)).isEqualTo(FALLBACK);
    }
}
