package com.example.demo.common.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarketHoursTest {

    private final MarketHours hours = MarketHours.defaults();

    // America/New_York is UTC-4 in June, so 18:00Z = 14:00 ET (open), 13:00Z = 09:00 ET (pre-open).
    @ParameterizedTest(name = "{0} -> open={1}")
    @CsvSource({
        "2024-06-12T18:00:00Z, true",   // Wed 14:00 ET — mid-session
        "2024-06-12T13:30:00Z, true",   // Wed 09:30 ET — exactly open
        "2024-06-12T13:00:00Z, false",  // Wed 09:00 ET — before open
        "2024-06-12T20:00:00Z, false",  // Wed 16:00 ET — exactly close (exclusive)
        "2024-06-15T18:00:00Z, false",  // Sat 14:00 ET — weekend
        "2024-06-16T18:00:00Z, false"   // Sun 14:00 ET — weekend
    })
    void isOpenReflectsTheSession(String instant, boolean open) {
        assertThat(hours.isOpen(Instant.parse(instant))).isEqualTo(open);
    }

    @Test
    void closeBoundaryIsExclusiveAndOpenIsInclusive() {
        assertSoftly(softly -> {
            softly.assertThat(hours.isOpen(Instant.parse("2024-06-12T13:30:00Z"))).isTrue();
            softly.assertThat(hours.isOpen(Instant.parse("2024-06-12T19:59:59Z"))).isTrue();
            softly.assertThat(hours.isOpen(Instant.parse("2024-06-12T20:00:00Z"))).isFalse();
        });
    }
}
