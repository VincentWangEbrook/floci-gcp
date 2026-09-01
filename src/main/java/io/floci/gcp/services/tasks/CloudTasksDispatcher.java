package io.floci.gcp.services.tasks;

import io.floci.gcp.core.common.EmulatorClock;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls due Cloud Tasks; test-control virtual time makes the next poll deterministic. */
@ApplicationScoped
public class CloudTasksDispatcher {
    private final CloudTasksService tasks;
    private final EmulatorClock clock;
    private final ScheduledExecutorService executor;

    @Inject
    public CloudTasksDispatcher(CloudTasksService tasks, EmulatorClock clock) {
        this.tasks = tasks; this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cloud-tasks-dispatcher"); t.setDaemon(true); return t; });
    }

    void onStart(@Observes StartupEvent ignored) {
        executor.scheduleAtFixedRate(() -> tasks.dispatchDue(clock.instant()), 1, 1, TimeUnit.SECONDS);
    }
    @PreDestroy void stop() { executor.shutdownNow(); }
}
