package io.floci.gcp.lifecycle;

import io.floci.gcp.config.EmulatorConfig;
import io.floci.gcp.core.common.ServiceRegistry;
import io.floci.gcp.core.storage.StorageFactory;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmulatorInfoControllerTest {

    @Test
    void resetIsNotAvailableWithoutTheExplicitTestControlProfile() {
        EmulatorConfig config = mock(EmulatorConfig.class);
        EmulatorConfig.TestControlConfig testControl = mock(EmulatorConfig.TestControlConfig.class);
        when(config.testControl()).thenReturn(testControl);
        when(testControl.enabled()).thenReturn(false);

        EmulatorInfoController controller = new EmulatorInfoController(
                mock(ServiceRegistry.class), mock(InitLifecycleState.class), config,
                mock(StorageFactory.class), mock(Instance.class));

        assertEquals(404, controller.reset().getStatus());
    }
}
