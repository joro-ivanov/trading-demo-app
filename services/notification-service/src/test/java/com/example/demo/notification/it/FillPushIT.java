package com.example.demo.notification.it;

import static com.example.demo.notification.it.harness.NotificationFixtures.FILLS;
import static com.example.demo.notification.it.harness.NotificationFixtures.OTHER_USER;
import static com.example.demo.notification.it.harness.NotificationFixtures.TESTER;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.StompProbe.Frames;

/** Kafka {@code fills} → STOMP {@code /user/queue/fills}, delivered only to the fill's owner. */
class FillPushIT extends NotificationITBase {

    private static final String FILL_SYMBOL = "AAPL";
    private static final String FILL = "{\"orderId\":\"o-1\",\"symbol\":\"" + FILL_SYMBOL + "\",\"side\":\"BUY\","
            + "\"quantity\":3,\"price\":190.40,\"owner\":\"" + TESTER + "\","
            + "\"filledAt\":\"2026-07-01T10:15:30Z\",\"sequenceId\":1}";
    /** How long the other user's queue is watched to prove the fill is not delivered there. */
    private static final Duration NO_DELIVERY_WINDOW = Duration.ofSeconds(1);

    @Test
    void deliversKafkaFillOnlyToItsOwner() throws Exception {
        Frames ownerFrames = stomp.subscribeToFills(stomp.connect(TESTER), TESTER);
        Frames otherFrames = stomp.subscribeToFills(stomp.connect(OTHER_USER), OTHER_USER);

        produce(FILLS, FILL_SYMBOL, FILL);

        String ownerFrame = ownerFrames.awaitFrame();
        List<String> leakedToOther = otherFrames.receivedWithin(NO_DELIVERY_WINDOW);
        assertSoftly(softly -> {
            softly.assertThat(ownerFrame).isEqualTo(FILL);
            softly.assertThat(leakedToOther).isEmpty();
        });
    }
}
