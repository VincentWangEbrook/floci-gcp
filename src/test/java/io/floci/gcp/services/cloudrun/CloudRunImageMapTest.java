package io.floci.gcp.services.cloudrun;

import io.floci.gcp.core.common.GcpException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudRunImageMapTest {

    @Test
    void resolvesOnlyAnExactConfiguredImageReference() {
        String localImage = CloudRunImageMap.resolve(
                "us-docker.pkg.dev/shophub-local/app/worker:2026-09-01",
                List.of("us-docker.pkg.dev/shophub-local/app/worker:2026-09-01=shophub-worker:local"));

        assertEquals("shophub-worker:local", localImage);
    }

    @Test
    void rejectsAnUnmappedImageBeforeDockerCanPullIt() {
        GcpException error = assertThrows(GcpException.class, () -> CloudRunImageMap.resolve(
                "us-docker.pkg.dev/shophub-local/app/worker:unmapped", List.of()));

        assertEquals("FAILED_PRECONDITION", error.getGcpStatus());
    }
}
