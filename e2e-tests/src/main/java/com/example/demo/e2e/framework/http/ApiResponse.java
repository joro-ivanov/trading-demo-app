package com.example.demo.e2e.framework.http;

import java.util.List;
import java.util.Map;

/**
 * Immutable result of an HTTP call. Exposes the raw body plus two deserialization helpers:
 * {@link #as(Class)} for a single object and {@link #asList(Class)} for a JSON array
 * (docs/DESIGN.md §8.1). Transport-agnostic: it never references the HTTP engine.
 */
public record ApiResponse(
        int statusCode,
        String body,
        Map<String, String> headers,
        Map<String, String> cookies,
        long durationMs) {

    /** Deserialize the body into a single object of {@code type}. */
    public <T> T as(Class<T> type) {
        return Json.read(body, type);
    }

    /** Deserialize the body (a JSON array) into a {@code List<type>}. */
    public <T> List<T> asList(Class<T> type) {
        return Json.readList(body, type);
    }
}
