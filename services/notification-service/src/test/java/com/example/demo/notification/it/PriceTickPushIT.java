package com.example.demo.notification.it;

import static com.example.demo.notification.it.harness.NotificationFixtures.ALICE;
import static com.example.demo.notification.it.harness.NotificationFixtures.BOB;
import static com.example.demo.notification.it.harness.NotificationFixtures.PRICE_TICKS;
import static com.example.demo.notification.it.harness.NotificationFixtures.TESTER;
import static com.example.demo.notification.it.harness.NotificationFixtures.priceTick;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;

import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.PriceTick;
import com.example.demo.notification.it.harness.RawJsonPayload;
import com.example.demo.notification.it.harness.StompProbe.Frames;

/**
 * Kafka {@code price.ticks} → STOMP {@code /topic/prices.<symbol>} broadcast. The payload is
 * forwarded verbatim, so each frame deserializes to exactly the tick that was produced. Each test uses its own symbol.
 */
class PriceTickPushIT extends NotificationITBase {

    private static final String SINGLE_SUBSCRIBER_SYMBOL = "AAPL";
    private static final BigDecimal SINGLE_SUBSCRIBER_PRICE = new BigDecimal("190.40");
    private static final String FAN_OUT_SYMBOL = "MSFT";
    private static final BigDecimal FAN_OUT_PRICE = new BigDecimal("410.10");
    private static final String POISONED_SYMBOL = "GOOG";
    private static final BigDecimal PRICE_AFTER_POISON = new BigDecimal("175.55");
    private static final String MALFORMED_TICK = "}{ not json";
    private static final String VERBATIM_SYMBOL = "NVDA";

    @Test
    void deliversKafkaPriceTickToSubscribedStompClient() throws Exception {
        StompSession session = stomp.connect(TESTER);
        Frames<PriceTick> frames = stomp.subscribeToPrices(session, SINGLE_SUBSCRIBER_SYMBOL);
        PriceTick tick = priceTick(SINGLE_SUBSCRIBER_SYMBOL, SINGLE_SUBSCRIBER_PRICE);

        produce(PRICE_TICKS, SINGLE_SUBSCRIBER_SYMBOL, tick);

        assertThat(frames.awaitFrame()).isEqualTo(tick);
    }

    // The gateway forwards the Kafka payload itself, not a re-serialized copy: key order, spacing and
    // the trailing zero survive, which a DTO round trip would normalise away.
    @Test
    @RawJsonPayload(reason = "byte-for-byte forwarding: only the raw frame text can show the payload was not re-serialized")
    void forwardsThePriceTickPayloadByteForByte() throws Exception {
        Frames<PriceTick> frames = stomp.subscribeToPrices(stomp.connect(TESTER), VERBATIM_SYMBOL);
        String unnormalisedTick = "{ \"price\" : 512.30,  \"symbol\" : \"" + VERBATIM_SYMBOL + "\" }";

        produceRaw(PRICE_TICKS, VERBATIM_SYMBOL, unnormalisedTick);

        assertThat(frames.awaitRawFrame()).isEqualTo(unnormalisedTick);
    }

    // One tick is broadcast to every subscriber of the symbol topic.
    @Test
    void fansOutOnePriceTickToMultipleSubscribers() throws Exception {
        Frames<PriceTick> framesA = stomp.subscribeToPrices(stomp.connect(ALICE), FAN_OUT_SYMBOL);
        Frames<PriceTick> framesB = stomp.subscribeToPrices(stomp.connect(BOB), FAN_OUT_SYMBOL);
        PriceTick tick = priceTick(FAN_OUT_SYMBOL, FAN_OUT_PRICE);

        produce(PRICE_TICKS, FAN_OUT_SYMBOL, tick);

        PriceTick frameA = framesA.awaitFrame();
        PriceTick frameB = framesB.awaitFrame();
        assertSoftly(softly -> {
            softly.assertThat(frameA).isEqualTo(tick);
            softly.assertThat(frameB).isEqualTo(tick);
        });
    }

    // A poison price tick (invalid JSON) must not stall the gateway — a following good tick still
    // arrives, and it is the first frame (the poison one is dropped, not forwarded).
    @Test
    @RawJsonPayload(reason = "a syntactically broken price tick (not JSON at all), which no PriceTick record can serialize to")
    void aMalformedPriceTickDoesNotStallTheGateway() throws Exception {
        Frames<PriceTick> frames = stomp.subscribeToPrices(stomp.connect(TESTER), POISONED_SYMBOL);
        PriceTick goodTick = priceTick(POISONED_SYMBOL, PRICE_AFTER_POISON);

        produceRaw(PRICE_TICKS, POISONED_SYMBOL, MALFORMED_TICK);
        produce(PRICE_TICKS, POISONED_SYMBOL, goodTick);

        assertThat(frames.awaitFrame()).isEqualTo(goodTick);
    }
}
