package com.example.demo.e2e.framework.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.demo.e2e.framework.config.Config;
import com.example.demo.e2e.framework.http.ApiRequest;
import com.example.demo.e2e.framework.http.ApiResponse;
import com.example.demo.e2e.framework.http.Json;
import com.example.demo.e2e.framework.http.RawBody;
import com.example.demo.e2e.framework.logging.TestLog;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Base of every API service object — the "dumb pipe" (docs/DESIGN.md §8.3).
 *
 * <p>This is the <b>only</b> class in the codebase that depends on the HTTP engine
 * (REST-Assured, {@code io.restassured..}). Swapping to JDK {@code HttpClient} means rewriting
 * {@link #send(String, ApiRequest)} and nothing else — guaranteed by an ArchUnit rule
 * (docs/DESIGN.md §13.1).
 *
 * <p>It performs <b>no input validation / defensive programming</b>: whatever the caller passes
 * is sent verbatim so negative scenarios are testable (docs/DESIGN.md §8.2). It also makes no
 * status assertions — the test decides what is correct.
 */
public abstract class BaseService {

    /** Execute against the default {@code base.url} (single-service / gateway setups). */
    protected static ApiResponse execute(ApiRequest request) {
        return execute(Config.get().baseUrl(), request);
    }

    /**
     * Execute against an explicit service base URL: resolve URL, log full request + response,
     * return the response. The backend is multiple services on different ports, so each
     * {@code Base*Service} supplies its own base URL (docs/DESIGN.md §8.3).
     */
    protected static ApiResponse execute(String baseUrl, ApiRequest request) {
        String targetUrl = baseUrl + resolvePath(request);
        String displayUrl = targetUrl + queryString(request);

        logRequest(displayUrl, request);
        ApiResponse response = send(targetUrl, request);
        logResponse(response);
        return response;
    }

    // ----------------------------------------------------------------------------------------
    // The ONLY place REST-Assured is referenced. Engine-swap = edit this method only.
    // ----------------------------------------------------------------------------------------
    private static ApiResponse send(String targetUrl, ApiRequest request) {
        RequestSpecification spec = RestAssured.given().relaxedHTTPSValidation();
        request.headers().forEach(spec::header);
        request.queryParams().forEach(spec::queryParam);
        request.cookies().forEach(spec::cookie);
        if (request.token() != null) {
            spec.header("Authorization", "Bearer " + request.token());
        }
        if (request.body() != null) {
            spec.contentType("application/json").body(payload(request.body()));
        }

        long start = System.nanoTime();
        Response raw = spec.request(request.method().name(), targetUrl);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : raw.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return new ApiResponse(
                raw.statusCode(),
                raw.getBody().asString(),
                Map.copyOf(headers),
                Map.copyOf(raw.getCookies()),
                durationMs);
    }

    /** A {@link RawBody} is sent verbatim; any other body is a DTO serialized to JSON. */
    private static String payload(Object body) {
        if (body instanceof RawBody raw) {
            return raw.json();
        }
        return Json.write(body);
    }

    private static String resolvePath(ApiRequest request) {
        String path = request.path();
        for (Map.Entry<String, String> param : request.pathParams().entrySet()) {
            path = path.replace("{" + param.getKey() + "}", param.getValue());
        }
        return path;
    }

    private static String queryString(ApiRequest request) {
        if (request.queryParams().isEmpty()) {
            return "";
        }
        return "?" + request.queryParams().entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void logRequest(String displayUrl, ApiRequest request) {
        String body = request.body() == null ? "<none>" : payload(request.body());
        String message = """
                → %s %s
                  headers : %s
                  cookies : %s
                  token   : %s
                  body    : %s"""
                .formatted(
                        request.method(),
                        displayUrl,
                        request.headers(),
                        request.cookies(),
                        request.token() == null ? "<none>" : "<present>",
                        body);
        TestLog.log(message);
    }

    private static void logResponse(ApiResponse response) {
        String message = """
                ← status %d (%d ms)
                  headers : %s
                  cookies : %s
                  body    : %s"""
                .formatted(
                        response.statusCode(),
                        response.durationMs(),
                        response.headers(),
                        response.cookies(),
                        response.body());
        TestLog.log(message);
    }
}
