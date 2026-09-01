package io.floci.gcp.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestFaultInjectorTest {
    @Test
    void armsOnlyWhitelistedOperationsAndConsumesEachFailureOnce() {
        TestFaultInjector injector = new TestFaultInjector(true);
        injector.arm("scheduler.dispatch", "simulated timeout");

        assertEquals("simulated timeout", injector.consume("scheduler.dispatch"));
        assertNull(injector.consume("scheduler.dispatch"));
        assertThrows(IllegalArgumentException.class, () -> injector.arm("arbitrary.command", "no"));
    }

    @Test
    void rejectsArmingOutsideTheTestProfile() {
        assertThrows(IllegalStateException.class,
                () -> new TestFaultInjector(false).arm("scheduler.dispatch", "no"));
    }
}
