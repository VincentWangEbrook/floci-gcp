package io.floci.gcp.core.common;

import io.floci.gcp.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot failures for isolated conformance tests; unavailable in ordinary profiles. */
@ApplicationScoped
public class TestFaultInjector {
    private static final Set<String> ALLOWED = Set.of("scheduler.dispatch", "tasks.dispatch", "run.invoke",
            "sql.connect", "secret.read", "iam.authorize", "logging.write");
    private final boolean enabled;
    private final ConcurrentHashMap<String, String> failures = new ConcurrentHashMap<>();

    @Inject
    public TestFaultInjector(EmulatorConfig config) { this(config.testControl().enabled()); }
    public TestFaultInjector(boolean enabled) { this.enabled = enabled; }

    public void arm(String operation, String message) {
        requireEnabled();
        if (!ALLOWED.contains(operation)) throw new IllegalArgumentException("Unsupported test fault operation: " + operation);
        failures.put(operation, message == null || message.isBlank() ? "Injected test failure" : message);
    }

    public String consume(String operation) { return failures.remove(operation); }
    private void requireEnabled() { if (!enabled) throw new IllegalStateException("Fault injection is available only when test-control is enabled"); }
}
