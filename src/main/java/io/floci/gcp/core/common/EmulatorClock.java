package io.floci.gcp.core.common;

import io.floci.gcp.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Shared clock whose mutable mode exists only for isolated emulator conformance tests. */
@ApplicationScoped
public class EmulatorClock {

    private final boolean testControlEnabled;
    private final AtomicReference<Instant> virtualInstant;

    @Inject
    public EmulatorClock(EmulatorConfig config) {
        this(config.testControl().enabled(), null);
    }

    public EmulatorClock(boolean testControlEnabled, Instant initialInstant) {
        this.testControlEnabled = testControlEnabled;
        this.virtualInstant = new AtomicReference<>(initialInstant);
    }

    public Instant instant() {
        Instant configured = virtualInstant.get();
        return configured == null ? Instant.now() : configured;
    }

    public Instant advance(Duration duration) {
        requireTestControl();
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Virtual time cannot move backwards");
        }
        return virtualInstant.updateAndGet(current -> (current == null ? Instant.now() : current).plus(duration));
    }

    public Instant set(Instant instant) {
        requireTestControl();
        virtualInstant.set(instant);
        return instant;
    }

    private void requireTestControl() {
        if (!testControlEnabled) {
            throw new IllegalStateException("Virtual time is available only when test-control is enabled");
        }
    }
}
