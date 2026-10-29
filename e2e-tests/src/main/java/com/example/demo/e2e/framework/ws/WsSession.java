package com.example.demo.e2e.framework.ws;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.common.json.Json;


/**
 * Stateful handle returned by the WebSocket service objects. Captures received frames into a
 * queue so tests can assert them with Awaitility (docs/DESIGN.md §8.4–§8.5).
 */
public final class WsSession {

    private final StompSession session;
    private final BlockingQueue<String> frames;

    WsSession(StompSession session, BlockingQueue<String> frames) {
        this.session = session;
        this.frames = frames;
    }

    /** Wait up to {@code timeout} for the next frame; returns {@code null} if none arrives. */
    public String nextFrame(Duration timeout) {
        try {
            return frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a WS frame", e);
        }
    }

    /** Wait for the next frame and deserialize it into {@code type}; returns {@code null} on timeout. */
    public <T> T nextFrameAs(Class<T> type, Duration timeout) {
        String frame = nextFrame(timeout);
        return frame == null ? null : Json.read(frame, type);
    }

    /** Snapshot of all frames received so far. */
    public List<String> frames() {
        return List.copyOf(frames);
    }

    public void close() {
        session.disconnect();
    }
}
