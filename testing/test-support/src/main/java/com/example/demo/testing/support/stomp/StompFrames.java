package com.example.demo.testing.support.stomp;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import com.example.demo.common.json.Json;
import com.example.demo.testing.RawJsonPayload;

/**
 * The frames received on one STOMP subscription, deserialized into {@code T}, with bounded waits so tests stay
 * linear. An optional sentinel frame (a readiness probe the harness produces until it arrives) is recorded as
 * {@link #sentinelSeen()} and never queued, so late sentinels never reach a test's assertions.
 */
public final class StompFrames<T> implements StompFrameHandler {

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final String sentinel;
    private final Class<T> type;
    private final Duration frameTimeout;
    private volatile boolean sentinelSeen;

    /** Frames of {@code type}, filtering out {@code sentinel} ({@code null} = none). */
    public StompFrames(Class<T> type, String sentinel, Duration frameTimeout) {
        this.type = type;
        this.sentinel = sentinel;
        this.frameTimeout = frameTimeout;
    }

    /** Whether the sentinel frame has arrived, i.e. the subscription is live. */
    public boolean sentinelSeen() {
        return sentinelSeen;
    }

    /** The next frame, waiting up to the frame timeout; fails the test if none arrives. */
    public T awaitFrame() throws InterruptedException {
        return Json.read(pollFrame(), type);
    }

    /** The next frame exactly as received, for a byte-for-byte forwarding check. */
    @RawJsonPayload(reason = "raw frame reader: proves the gateway forwards the Kafka payload byte for byte")
    public String awaitRawFrame() throws InterruptedException {
        return pollFrame();
    }

    /** Every frame that arrives within {@code window} (empty when nothing does). */
    public List<T> receivedWithin(Duration window) throws InterruptedException {
        List<String> frames = new ArrayList<>();
        String first = received.poll(window.toMillis(), TimeUnit.MILLISECONDS);
        if (first != null) {
            frames.add(first);
            received.drainTo(frames);
        }
        return frames.stream().map(frame -> Json.read(frame, type)).toList();
    }

    private String pollFrame() throws InterruptedException {
        String frame = received.poll(frameTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (frame == null) {
            throw new AssertionError("No STOMP frame received within " + frameTimeout);
        }
        return frame;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        String frame = (String) payload;
        if (frame.equals(sentinel)) {
            sentinelSeen = true;
        } else {
            received.add(frame);
        }
    }
}
