package io.floci.gcp.services.scheduler;

import io.floci.gcp.core.common.TestFaultInjector;
import io.floci.gcp.services.scheduler.model.StoredJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleInvokerFaultInjectionTest {
    @Test
    void returnsAnInjectedFailureWithoutDispatchingTheJob() {
        TestFaultInjector faults = new TestFaultInjector(true);
        faults.arm("scheduler.dispatch", "simulated scheduler failure");
        ScheduleInvoker invoker = new ScheduleInvoker(null, faults);
        StoredJob job = new StoredJob();
        job.setName("projects/p/locations/l/jobs/j");
        job.setTargetType("PUBSUB");

        ScheduleInvoker.InvokeResult result = invoker.invoke(job);

        assertEquals(2, result.code());
        assertEquals("simulated scheduler failure", result.message());
    }
}
