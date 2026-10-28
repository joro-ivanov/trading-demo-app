package com.example.demo.notification.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.StompProbe.Frames;

/**
 * Kafka {@code price.ticks} → STOMP {@code /topic/prices.<symbol>} broadcast. The payload is
 * forwarded verbatim, so frames are asserted for exact equality. Each test uses its own symbol.
 */
class PriceTickPushIT extends NotificationITBase {

    @Test
    void deliversKafkaPriceTickToSubscribedStompClient() throws Exception {
        StompSession session = stomp.connect("tester");
        Frames frames = stomp.subscribeToPrices(session, "AAPL");

        produce("price.ticks", "AAPL", "{\"symbol\":\"AAPL\",\"price\":190.40}");

        assertThat(frames.awaitFrame()).isEqualTo("{\"symbol\":\"AAPL\",\"price\":190.40}");
    }

    // One tick is broadcast to every subscriber of the symbol topic.
    @Test
    void fansOutOnePriceTickToMultipleSubscribers() throws Exception {
        Frames framesA = stomp.subscribeToPrices(stomp.connect("alice"), "MSFT");
        Frames framesB = stomp.subscribeToPrices(stomp.connect("bob"), "MSFT");

        produce("price.ticks", "MSFT", "{\"symbol\":\"MSFT\",\"price\":410.10}");

        String frameA = framesA.awaitFrame();
        String frameB = framesB.awaitFrame();
        assertSoftly(softly -> {
            softly.assertThat(frameA).isEqualTo("{\"symbol\":\"MSFT\",\"price\":410.10}");
            softly.assertThat(frameB).isEqualTo("{\"symbol\":\"MSFT\",\"price\":410.10}");
        });
    }

    // A poison price tick (invalid JSON) must not stall the gateway — a following good tick still
    // arrives, and it is the first frame (the poison one is dropped, not forwarded).
    @Test
    void aMalformedPriceTickDoesNotStallTheGateway() throws Exception {
        Frames frames = stomp.subscribeToPrices(stomp.connect("tester"), "GOOG");

        produce("price.ticks", "GOOG", "}{ not json");
        produce("price.ticks", "GOOG", "{\"symbol\":\"GOOG\",\"price\":175.55}");

        assertThat(frames.awaitFrame()).isEqualTo("{\"symbol\":\"GOOG\",\"price\":175.55}");
    }
}
