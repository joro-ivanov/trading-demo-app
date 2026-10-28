package com.example.demo.notification.it;

import static com.example.demo.notification.it.harness.NotificationFixtures.FILLS;
import static com.example.demo.notification.it.harness.NotificationFixtures.OTHER_USER;
import static com.example.demo.notification.it.harness.NotificationFixtures.TESTER;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.notification.it.harness.FillEvent;
import com.example.demo.notification.it.harness.NotificationITBase;
import com.example.demo.notification.it.harness.StompProbe.Frames;

/** Kafka {@code fills} → STOMP {@code /user/queue/fills}, delivered only to the fill's owner. */
class FillPushIT extends NotificationITBase {

    private static final String FILL_ORDER_ID = "o-1";
    private static final String FILL_SYMBOL = "AAPL";
    private static final String FILL_SIDE = "BUY";
    private static final BigDecimal FILL_QUANTITY = new BigDecimal("3");
    private static final BigDecimal FILL_PRICE = new BigDecimal("190.40");
    private static final Instant FILLED_AT = Instant.parse("2026-07-01T10:15:30Z");
    private static final long FILL_SEQUENCE_ID = 1;
    private static final FillEvent FILL = new FillEvent(FILL_ORDER_ID, FILL_SYMBOL, FILL_SIDE, FILL_QUANTITY,
            FILL_PRICE, TESTER, FILLED_AT, FILL_SEQUENCE_ID);
    /** How long the other user's queue is watched to prove the fill is not delivered there. */
    private static final Duration NO_DELIVERY_WINDOW = Duration.ofSeconds(1);

    @Test
    void deliversKafkaFillOnlyToItsOwner() throws Exception {
        Frames<FillEvent> ownerFrames = stomp.subscribeToFills(stomp.connect(TESTER), TESTER);
        Frames<FillEvent> otherFrames = stomp.subscribeToFills(stomp.connect(OTHER_USER), OTHER_USER);

        produce(FILLS, FILL_SYMBOL, FILL);

        FillEvent ownerFrame = ownerFrames.awaitFrame();
        List<FillEvent> leakedToOther = otherFrames.receivedWithin(NO_DELIVERY_WINDOW);
        assertSoftly(softly -> {
            softly.assertThat(ownerFrame).isEqualTo(FILL);
            softly.assertThat(leakedToOther).isEmpty();
        });
    }
}
