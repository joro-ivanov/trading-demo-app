package com.example.demo.e2e.harness;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.example.demo.common.json.Json;

/** The one "is it up?" probe of the e2e harness: does an HTTP request to the stack answer 200 in time. */
final class Reachability {

    private static final Duration TIMEOUT = Duration.ofMillis(1500);
    private static final int OK = 200;
    private static final String POST = "POST";

    private Reachability() {
    }

    /** {@code GET url} answers 200. */
    static boolean getsOk(String url) {
        try {
            HttpURLConnection connection = open(url);
            return answersOk(connection);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code POST url} with {@code body} serialized as JSON answers 200. */
    static boolean postsOk(String url, Object body) {
        try {
            HttpURLConnection connection = open(url);
            connection.setRequestMethod(POST);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(Json.write(body).getBytes(StandardCharsets.UTF_8));
            }
            return answersOk(connection);
        } catch (IOException e) {
            return false;
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout((int) TIMEOUT.toMillis());
        connection.setReadTimeout((int) TIMEOUT.toMillis());
        return connection;
    }

    private static boolean answersOk(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        connection.disconnect();
        return status == OK;
    }
}
