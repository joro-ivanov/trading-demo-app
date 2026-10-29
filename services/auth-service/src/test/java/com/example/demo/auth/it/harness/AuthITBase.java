package com.example.demo.auth.it.harness;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.testing.support.containers.TestContainers;
import com.example.demo.testing.support.it.ServiceITBase;

/**
 * Shared harness for the auth-service integration tests (docs/DESIGN.md §7.2): the real service on a
 * random port ({@link ServiceITBase}) against a real Postgres (Testcontainers). Flyway seeds the demo
 * users (alice/bob/demo, password {@code password}); every test starts from exactly that seed (users
 * registered by earlier tests are removed), so user lists can be asserted exactly. The container is a
 * singleton started once per JVM, so every {@code *IT} class shares it and the cached Spring context.
 */
public abstract class AuthITBase extends ServiceITBase {

    static final PostgreSQLContainer POSTGRES = TestContainers.postgres();

    static {
        POSTGRES.start();
    }

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainers.datasource(registry, POSTGRES);
    }

    /** Back to the Flyway seed (u1 alice, u2 bob, u3 demo) before every test. */
    @BeforeEach
    void resetToSeededUsers() {
        jdbc.update("delete from users where id not in ('u1', 'u2', 'u3')");
    }
}
