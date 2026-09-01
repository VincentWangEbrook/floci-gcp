package io.floci.gcp.core.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmulatorClockTest {

    @Test
    void advancesOnlyWhenTestControlIsEnabled() {
        EmulatorClock clock = new EmulatorClock(true, Instant.parse("2026-01-01T00:00:00Z"));

        clock.advance(Duration.ofMinutes(5));

        assertEquals(Instant.parse("2026-01-01T00:05:00Z"), clock.instant());
    }

    @Test
    void rejectsVirtualTimeOperationsOutsideTestControl() {
        EmulatorClock clock = new EmulatorClock(false, Instant.parse("2026-01-01T00:00:00Z"));

        assertThrows(IllegalStateException.class, () -> clock.advance(Duration.ofSeconds(1)));
    }
}
