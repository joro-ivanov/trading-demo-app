package com.example.demo.integration.harness;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.common.json.Json;
import com.example.demo.testing.support.auth.TestJwt;
import com.example.demo.testing.support.it.ApiSpec;
import com.example.demo.testing.support.stomp.StompClient;
import com.example.demo.testing.support.stomp.StompFrames;

/**
 * Orchestration helpers for the cross-service flagship test. Each service is launched as an
 * <b>isolated subprocess</b> from its executable {@code -boot} jar — so classpaths,
 * auto-configurations, {@code application.yaml} files, and Flyway migrations never collide (the
 * problem an in-JVM multi-context approach hits). Kept out of the {@code *IT} class so the test
 * methods stay free of process/socket plumbing.
 */
public final class Stack {

    private static final String JAVA_BIN = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    /** Graceful shutdown budget per service process (Spring's default per-phase timeout is 30 s). */
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    /** Upper bound for a STOMP CONNECT to complete. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private Stack() {
    }

    /** Launch a service jar as a subprocess; stdout/stderr go to {@code target/<logName>}. */
    public static Process launch(String jar, String logName, Map<String, String> overrides) {
        List<String> command = new ArrayList<>();
        command.add(JAVA_BIN);
        command.add("-jar");
        command.add(jar);
        overrides.forEach((key, value) -> command.add("--" + key + "=" + value));

        File log = new File("target", logName);
        try {
            return new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log)
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reserve an ephemeral free port. */
    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Wait until {@code port} accepts TCP connections (the service's web server has started). */
    public static void awaitListening(int port, Duration timeout) {
        Awaitility.await().atMost(timeout).pollInterval(Duration.ofMillis(500)).until(() -> tcpOpen(port));
    }

    /** Wait until {@code GET http://localhost:port/path} returns 200 with the given bearer token. */
    public static void awaitHttpOk(int port, String path, String token, Duration timeout) {
        Awaitility.await().atMost(timeout).pollInterval(Duration.ofMillis(500))
                .until(() -> httpOk(port, path, token));
    }

    /** Connects a STOMP-over-SockJS client to notification-service with {@code Authorization: Bearer <token>}. */
    public static StompSession connect(int port, String token) throws Exception {
        return StompClient.connect(port, TestJwt.bearer(token)).get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Subscribes {@code destination}; frames are deserialized into {@code type}, waiting up to {@code frameTimeout} each. */
    public static <T> StompFrames<T> subscribe(StompSession session, String destination, Class<T> type,
                                               Duration frameTimeout) {
        StompFrames<T> frames = new StompFrames<>(type, null, frameTimeout);
        session.subscribe(destination, frames);
        return frames;
    }

    public static void post(int port, String token, String path, Object body, int expectedStatus) {
        ApiSpec.api(port, token).body(body)
        .when()
                .post(path)
        .then()
                .statusCode(expectedStatus);
    }

    public static <T> T post(int port, String token, String path, Object body, int expectedStatus, Class<T> type) {
        return ApiSpec.api(port, token).body(body)
        .when()
                .post(path)
        .then()
                .statusCode(expectedStatus)
                .extract().as(type);
    }

    public static <T> List<T> getList(int port, String token, String path, Class<T> type) {
        String response = ApiSpec.api(port, token)
        .when()
                .get(path)
        .then()
                .statusCode(200)
                .extract().asString();
        return Json.readList(response, type);
    }

    /**
     * Stops the service processes and <b>waits for them to exit</b>, so each finishes its graceful
     * shutdown (Kafka consumers/producers, Redis, DB pools) before the test stops the shared containers.
     * Without the wait the containers disappeared mid-shutdown (DisconnectException / Redis reconnect noise).
     * A process still alive after {@link #SHUTDOWN_TIMEOUT} is killed and reported as a failure.
     */
    public static void destroy(List<Process> processes) {
        processes.forEach(Process::destroy);
        List<Long> killed = new ArrayList<>();
        for (Process process : processes) {
            if (!exitsWithin(process, SHUTDOWN_TIMEOUT)) {
                process.destroyForcibly();
                killed.add(process.pid());
            }
        }
        if (!killed.isEmpty()) {
            throw new IllegalStateException("Service processes did not stop within " + SHUTDOWN_TIMEOUT
                    + " and were killed: " + killed);
        }
    }

    private static boolean exitsWithin(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean tcpOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean httpOk(int port, String path, String token) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
            connection.setRequestProperty("Authorization", TestJwt.bearer(token));
            connection.setConnectTimeout(800);
            connection.setReadTimeout(800);
            int status = connection.getResponseCode();
            connection.disconnect();
            return status == 200;
        } catch (IOException e) {
            return false;
        }
    }
}
