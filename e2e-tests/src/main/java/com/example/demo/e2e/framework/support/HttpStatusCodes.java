package com.example.demo.e2e.framework.support;

/** HTTP status codes and the reason phrases the services put in an error body's {@code error} field. */
public final class HttpStatusCodes {

    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int UNPROCESSABLE_CONTENT = 422;

    public static final String BAD_REQUEST_REASON = "Bad Request";
    public static final String UNAUTHORIZED_REASON = "Unauthorized";
    public static final String NOT_FOUND_REASON = "Not Found";
    public static final String CONFLICT_REASON = "Conflict";
    public static final String UNPROCESSABLE_CONTENT_REASON = "Unprocessable Content";

    private HttpStatusCodes() {
    }
}
