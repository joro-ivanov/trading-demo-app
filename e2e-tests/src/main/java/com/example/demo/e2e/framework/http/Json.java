package com.example.demo.e2e.framework.http;

import java.util.List;

/**
 * Tiny JSON facade over Jackson 3 (tools.jackson) used by {@link ApiResponse} and by
 * {@code BaseService} for request-body serialization. Kept independent of the HTTP engine so
 * that {@link ApiResponse} stays transport-agnostic (docs/DESIGN.md §13.1).
 *
 * <p>Jackson 3 throws {@code JacksonException} (unchecked), so there is no checked-exception
 * handling here — deserialization failures surface naturally to the asserting test.
 */
public final class Json {

    private Json() {
    }

    /** Serialize any value to a JSON string. */
    public static String write(Object value) {
        return com.example.demo.common.json.Json.write(value);
    }

    /** Deserialize a JSON object into {@code type}. */
    public static <T> T read(String json, Class<T> type) {
        return com.example.demo.common.json.Json.read(json, type);
    }

    /** Deserialize a JSON array into a {@code List<type>}. */
    public static <T> List<T> readList(String json, Class<T> type) {
        return com.example.demo.common.json.Json.readList(json, type);
    }
}
