package com.example.demo.e2e.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void readsValueFromPropertiesFile() {
        assertThat(Config.get().get("auth.username")).isEqualTo("demo");
    }

    @Test
    void systemPropertyOverridesFileValue() {
        System.setProperty("auth.username", "overridden");

        String resolved = Config.get().get("auth.username");
        System.clearProperty("auth.username");

        assertThat(resolved).isEqualTo("overridden");
    }

    @Test
    void defaultUsedWhenKeyAbsentEverywhere() {
        assertThat(Config.get().get("does.not.exist", "fallback")).isEqualTo("fallback");
    }
}
