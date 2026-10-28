package com.example.demo.notification.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.StompProbe.Frames;

/** Kafka {@code fills} → STOMP {@code /user/queue/fills}, delivered only to the fill's owner. */
class FillPushIT extends NotificationITBase {

    private static final String FILL = "{\"orderId\":\"o-1\",\"symbol\":\"AAPL\",\"side\":\"BUY\","
            + "\"quantity\":3,\"price\":190.40,\"owner\":\"tester\","
            + "\"filledAt\":\"2026-07-01T10:15:30Z\",\"sequenceId\":1}";

    @Test
    void deliversKafkaFillOnlyToItsOwner() throws Exception {
        Frames ownerFrames = stomp.subscribeToFills(stomp.connect("tester"), "tester");
        Frames otherFrames = stomp.subscribeToFills(stomp.connect("other"), "other");

        produce("fills", "AAPL", FILL);

        String ownerFrame = ownerFrames.awaitFrame();
        List<String> leakedToOther = otherFrames.receivedWithin(Duration.ofSeconds(1));
        assertSoftly(softly -> {
            softly.assertThat(ownerFrame).isEqualTo(FILL);
            softly.assertThat(leakedToOther).isEmpty();
        });
    }
}
