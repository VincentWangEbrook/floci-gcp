package io.floci.gcp.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateSchemaManagerTest {

    @TempDir
    Path stateDirectory;

    @Test
    void initializesAnUnversionedPersistentDirectoryWithoutDeletingItsData() throws IOException {
        Path existingState = stateDirectory.resolve("cloud-tasks.json");
        Files.writeString(existingState, "{\"task\":\"preserved\"}");

        new StateSchemaManager(stateDirectory, "persistent").ensureCompatible();

        assertEquals("{\"task\":\"preserved\"}", Files.readString(existingState));
        assertTrue(Files.exists(stateDirectory.resolve(StateSchemaManager.METADATA_FILE)));
        assertEquals(1, StateSchemaManager.readSchemaVersion(
                stateDirectory.resolve(StateSchemaManager.METADATA_FILE)));
    }

    @Test
    void rejectsStateFromANewerSchemaAndExplainsHowToReset() throws IOException {
        Path metadata = stateDirectory.resolve(StateSchemaManager.METADATA_FILE);
        Files.writeString(metadata, "{\"schemaVersion\":2}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new StateSchemaManager(stateDirectory, "persistent").ensureCompatible());

        assertTrue(error.getMessage().contains("schema version 2"));
        assertTrue(error.getMessage().contains("reset"));
    }

    @Test
    void doesNotCreateStateMetadataForInMemoryStorage() {
        new StateSchemaManager(stateDirectory, "memory").ensureCompatible();

        assertFalse(Files.exists(stateDirectory.resolve(StateSchemaManager.METADATA_FILE)));
    }
}
