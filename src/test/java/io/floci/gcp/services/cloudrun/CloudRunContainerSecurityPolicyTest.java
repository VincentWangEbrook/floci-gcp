package io.floci.gcp.services.cloudrun;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Volume;
import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.docker.ContainerSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudRunContainerSecurityPolicyTest {

    @Test
    void acceptsAnUnprivilegedContainerWithNamedVolumesOnly() {
        ContainerSpec safe = new ContainerSpec("example@sha256:abc", null, List.of("PORT=8080"), null,
                null, null, Map.of(), List.of(), null,
                List.of(new Mount().withType(MountType.VOLUME).withSource("gcs-snapshot").withTarget("/data")),
                List.of(), List.of(), Map.of(), null, false, null, List.of(), null, null, List.of());

        assertDoesNotThrow(() -> CloudRunContainerSecurityPolicy.validate(safe));
    }

    @Test
    void rejectsPrivilegedContainers() {
        assertThrows(GcpException.class, () -> CloudRunContainerSecurityPolicy.validate(spec(true, List.of(), List.of(), List.of())));
    }

    @Test
    void rejectsHostBindAndDockerSocketMounts() {
        assertThrows(GcpException.class, () -> CloudRunContainerSecurityPolicy.validate(
                spec(false, List.of(), List.of(new Bind("/tmp", new Volume("/data"))), List.of())));
        assertThrows(GcpException.class, () -> CloudRunContainerSecurityPolicy.validate(
                spec(false, List.of(new Mount().withType(MountType.BIND)
                        .withSource("/var/run/docker.sock").withTarget("/var/run/docker.sock")), List.of(), List.of())));
    }

    @Test
    void rejectsRealGcpCredentialEnvironmentVariables() {
        assertThrows(GcpException.class, () -> CloudRunContainerSecurityPolicy.validate(
                spec(false, List.of(), List.of(), List.of("GOOGLE_APPLICATION_CREDENTIALS=/secrets/key.json"))));
    }

    private static ContainerSpec spec(boolean privileged, List<Mount> mounts, List<Bind> binds, List<String> env) {
        return new ContainerSpec("example@sha256:abc", null, env, null, null, null, Map.of(), List.of(), null,
                mounts, binds, List.of(), Map.of(), null, privileged, null, List.of(), null, null, List.of());
    }
}
