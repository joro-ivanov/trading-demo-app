package com.example.demo.notification.it;

import static com.example.demo.notification.it.harness.NotificationFixtures.ALICE;
import static com.example.demo.notification.it.harness.NotificationFixtures.BOB;
import static com.example.demo.notification.it.harness.NotificationFixtures.PRICE_TICKS;
import static com.example.demo.notification.it.harness.NotificationFixtures.TESTER;
import static com.example.demo.notification.it.harness.NotificationFixtures.priceTick;
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

    private static final String SINGLE_SUBSCRIBER_SYMBOL = "AAPL";
    private static final String SINGLE_SUBSCRIBER_PRICE = "190.40";
    private static final String FAN_OUT_SYMBOL = "MSFT";
    private static final String FAN_OUT_PRICE = "410.10";
    private static final String POISONED_SYMBOL = "GOOG";
    private static final String PRICE_AFTER_POISON = "175.55";
    private static final String MALFORMED_TICK = "}{ not json";

    @Test
    void deliversKafkaPriceTickToSubscribedStompClient() throws Exception {
        StompSession session = stomp.connect(TESTER);
        Frames frames = stomp.subscribeToPrices(session, SINGLE_SUBSCRIBER_SYMBOL);
        String tick = priceTick(SINGLE_SUBSCRIBER_SYMBOL, SINGLE_SUBSCRIBER_PRICE);

        produce(PRICE_TICKS, SINGLE_SUBSCRIBER_SYMBOL, tick);

        assertThat(frames.awaitFrame()).isEqualTo(tick);
    }

    // One tick is broadcast to every subscriber of the symbol topic.
    @Test
    void fansOutOnePriceTickToMultipleSubscribers() throws Exception {
        Frames framesA = stomp.subscribeToPrices(stomp.connect(ALICE), FAN_OUT_SYMBOL);
        Frames framesB = stomp.subscribeToPrices(stomp.connect(BOB), FAN_OUT_SYMBOL);
        String tick = priceTick(FAN_OUT_SYMBOL, FAN_OUT_PRICE);

        produce(PRICE_TICKS, FAN_OUT_SYMBOL, tick);

        String frameA = framesA.awaitFrame();
        String frameB = framesB.awaitFrame();
        assertSoftly(softly -> {
            softly.assertThat(frameA).isEqualTo(tick);
            softly.assertThat(frameB).isEqualTo(tick);
        });
    }

    // A poison price tick (invalid JSON) must not stall the gateway — a following good tick still
    // arrives, and it is the first frame (the poison one is dropped, not forwarded).
    @Test
    void aMalformedPriceTickDoesNotStallTheGateway() throws Exception {
        Frames frames = stomp.subscribeToPrices(stomp.connect(TESTER), POISONED_SYMBOL);
        String goodTick = priceTick(POISONED_SYMBOL, PRICE_AFTER_POISON);

        produce(PRICE_TICKS, POISONED_SYMBOL, MALFORMED_TICK);
        produce(PRICE_TICKS, POISONED_SYMBOL, goodTick);

        assertThat(frames.awaitFrame()).isEqualTo(goodTick);
    }
}
