package com.example.demo.e2e.framework.http;

/**
 * A hand-written request body, sent verbatim instead of being serialized. Only
 * {@link ApiRequest.Builder#rawBody(String)} creates one, and only a {@code @RawJsonPayload} test may
 * call that (ArchUnit {@code rawBodiesOnlyFromRawJsonPayloadTests}).
 */
public record RawBody(String json) {
}
