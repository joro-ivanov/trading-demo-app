package com.example.demo.e2e.harness;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.model.LoginRequest;

/**
 * Asserts the backend is already running before the API e2e tests execute. The e2e tests run
 * against a <b>deployed environment</b> — locally a manually started {@code docker compose up}
 * stack — and never boot anything (docs/DESIGN.md §7). Reachability is probed via the public
 * {@code POST /auth/login} (a meaningful "stack up + auth working" signal). If it fails, the suite
 * is skipped. Target URLs come from {@code Config} ({@code -D}/env override per environment).
 */
public final class RequireRunningBackend implements BeforeAllCallback {

    /** The seeded demo user (auth V1__users.sql) the probe logs in as, from the e2e config. */
    private static final String PROBE_USERNAME = Config.get().get("auth.username");
    private static final String PROBE_PASSWORD = Config.get().get("auth.password");

    @Override
    public void beforeAll(ExtensionContext context) {
        String url = Config.get().get("auth.base.url") + "/auth/login";
        Assumptions.assumeTrue(Reachability.postsOk(url, new LoginRequest(PROBE_USERNAME, PROBE_PASSWORD)),
                "Backend not reachable at " + url + " — start it with: docker compose up");
    }
}
