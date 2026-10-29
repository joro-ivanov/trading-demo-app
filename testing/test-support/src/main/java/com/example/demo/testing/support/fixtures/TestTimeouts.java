package com.example.demo.testing.support.fixtures;

import java.time.Duration;

/** How long the backend tests wait for asynchronous effects. */
public final class TestTimeouts {

    /** Upper bound for an asynchronous effect (a Kafka event, a STOMP frame, a DB change) to become visible. */
    public static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);

    private TestTimeouts() {
    }
}
