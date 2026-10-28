package com.example.demo.e2e.harness;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.Json;
import com.example.demo.e2e.framework.model.LoginRequest;

/**
 * Asserts the backend is already running before the API e2e tests execute. The e2e tests run
 * against a <b>deployed environment</b> — locally a manually started {@code docker compose up}
 * stack — and never boot anything (docs/DESIGN.md §7). Reachability is probed via the public
 * {@code POST /auth/login} (a meaningful "stack up + auth working" signal). If it fails, the suite
 * is skipped. Target URLs come from {@code Config} ({@code -D}/env override per environment).
 */
public final class RequireRunningBackend implements BeforeAllCallback {

    private static final String PROBE_USERNAME = "demo";
    private static final String PROBE_PASSWORD = "password";

    @Override
    public void beforeAll(ExtensionContext context) {
        String url = Config.get().get("auth.base.url") + "/auth/login";
        Assumptions.assumeTrue(loginReachable(url),
                "Backend not reachable at " + url + " — start it with: docker compose up");
    }

    private static boolean loginReachable(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(Json.write(new LoginRequest(PROBE_USERNAME, PROBE_PASSWORD)).getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            connection.disconnect();
            return status == 200;
        } catch (IOException e) {
            return false;
        }
    }
}
