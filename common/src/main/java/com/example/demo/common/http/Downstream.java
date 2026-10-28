package com.example.demo.common.http;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates a failed call to another service into the status this service answers with, so a
 * downstream problem is never reported as a 500 (which is reserved for bugs in this service):
 * <ul>
 *   <li>the dependency could not be reached or did not answer in time (an outage) → <b>503</b>;</li>
 *   <li>the dependency answered, but with an error status or an empty/unreadable body → <b>502</b>.</li>
 * </ul>
 * Expected answers (e.g. a 404 meaning "unknown symbol") are handled by the caller before this.
 */
public final class Downstream {

    private Downstream() {
    }

    /** {@code cause} is the client exception (or the exception wrapping it) from the failed call. */
    public static ResponseStatusException failure(String dependency, Throwable cause) {
        return isUnreachable(cause)
                ? new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, dependency + " unavailable", cause)
                : invalidResponse(dependency, cause);
    }

    /** The dependency answered, but the answer is unusable (error status, empty or unreadable body). */
    public static ResponseStatusException invalidResponse(String dependency, Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, dependency + " returned an invalid response", cause);
    }

    private static boolean isUnreachable(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof ResourceAccessException) {
                return true;
            }
        }
        return false;
    }
}
