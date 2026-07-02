package com.example.demo.integration;

import static io.restassured.RestAssured.given;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.example.demo.common.json.Json;

import io.restassured.http.ContentType;

/**
 * Orchestration helpers for the cross-service flagship test. Each service is launched as an
 * <b>isolated subprocess</b> from its executable {@code -boot} jar — so classpaths,
 * auto-configurations, {@code application.yaml} files, and Flyway migrations never collide (the
 * problem an in-JVM multi-context approach hits). Kept out of the {@code *IT} class so the test
 * methods stay free of process/socket plumbing.
 */
final class Stack {

    private static final String JAVA_BIN = Path.of(System.getProperty("java.home"), "bin", "java").toString();

    private Stack() {
    }

    /** Launch a service jar as a subprocess; stdout/stderr go to {@code target/<logName>}. */
    static Process launch(String jar, String logName, Map<String, String> overrides) {
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
    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Wait until {@code port} accepts TCP connections (the service's web server has started). */
    static void awaitListening(int port, Duration timeout) {
        Awaitility.await().atMost(timeout).pollInterval(Duration.ofMillis(500)).until(() -> tcpOpen(port));
    }

    /** Wait until {@code GET http://localhost:port/path} returns 200 with the given bearer token. */
    static void awaitHttpOk(int port, String path, String token, Duration timeout) {
        Awaitility.await().atMost(timeout).pollInterval(Duration.ofMillis(500))
                .until(() -> httpOk(port, path, token));
    }

    /** Connect a STOMP-over-SockJS client and subscribe a destination, draining frames into {@code sink}. */
    static StompSession subscribe(int port, String destination, String token, BlockingQueue<String> sink)
            throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = client
                .connectAsync("http://localhost:" + port + "/ws",
                        new org.springframework.web.socket.WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((String) payload);
            }
        });
        return session;
    }

    static void post(int port, String token, String path, Object body, int expectedStatus) {
        given().port(port).header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Json.write(body))
        .when()
                .post(path)
        .then()
                .statusCode(expectedStatus);
    }

    static <T> T post(int port, String token, String path, Object body, int expectedStatus, Class<T> type) {
        String response = given().port(port).header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Json.write(body))
        .when()
                .post(path)
        .then()
                .statusCode(expectedStatus)
                .extract().asString();
        return Json.read(response, type);
    }

    static <T> List<T> getList(int port, String token, String path, Class<T> type) {
        String response = given().port(port).header("Authorization", "Bearer " + token)
        .when()
                .get(path)
        .then()
                .statusCode(200)
                .extract().asString();
        return Json.readList(response, type);
    }

    static <T> T frameAs(String frame, Class<T> type) {
        return Json.read(frame, type);
    }

    static void destroy(List<Process> processes) {
        processes.forEach(Process::destroy);
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
            connection.setRequestProperty("Authorization", "Bearer " + token);
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
