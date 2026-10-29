package com.example.demo.e2e.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.demo.e2e.BaseTest;
import com.example.demo.e2e.framework.allure.TestLogArtifacts;
import com.example.demo.e2e.harness.RequireRunningBackend;

/** Every API e2e test: tagged {@code api}, skipped without a running backend, per-test HTTP log in the report. */
@Tag("api")
@ExtendWith(RequireRunningBackend.class)
@ExtendWith(TestLogArtifacts.class)
abstract class BaseApiTest extends BaseTest {
}
