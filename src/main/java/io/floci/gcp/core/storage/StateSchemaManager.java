package io.floci.gcp.core.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Guards the on-disk state format before any service loads persisted JSON files.
 *
 * <p>The first versioned release adopts pre-existing unversioned directories without rewriting
 * their service data. Later releases must either add a non-destructive migration here or reject
 * an incompatible directory with an explicit reset instruction.
 */
public final class StateSchemaManager {

    static final String METADATA_FILE = ".floci-gcp-state.json";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final Path stateDirectory;
    private final String storageMode;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StateSchemaManager(Path stateDirectory, String storageMode) {
        this.stateDirectory = stateDirectory;
        this.storageMode = storageMode;
    }

    /** Ensures persistent state is known to be compatible before it is read. */
    public void ensureCompatible() {
        if (!usesDisk()) {
            return;
        }

        Path metadata = stateDirectory.resolve(METADATA_FILE);
        try {
            Files.createDirectories(stateDirectory);
            if (!Files.exists(metadata)) {
                writeMetadata(metadata);
                return;
            }

            int storedVersion = readSchemaVersion(metadata);
            if (storedVersion != CURRENT_SCHEMA_VERSION) {
                throw incompatibleVersion(storedVersion);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read floci-gcp state metadata at " + metadata
                    + ". State was left untouched; fix the file or reset the local state directory.", e);
        }
    }

    static int readSchemaVersion(Path metadata) throws IOException {
        JsonNode root = new ObjectMapper().readTree(metadata.toFile());
        JsonNode version = root == null ? null : root.get("schemaVersion");
        if (version == null || !version.canConvertToInt()) {
            throw new IOException("Missing numeric schemaVersion");
        }
        return version.intValue();
    }

    private boolean usesDisk() {
        return "persistent".equals(storageMode) || "hybrid".equals(storageMode) || "wal".equals(storageMode);
    }

    private void writeMetadata(Path metadata) throws IOException {
        Path temp = metadata.resolveSibling(METADATA_FILE + ".tmp");
        objectMapper.writeValue(temp.toFile(), new StateMetadata(CURRENT_SCHEMA_VERSION));
        try {
            Files.move(temp, metadata, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveError) {
            Files.move(temp, metadata, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private IllegalStateException incompatibleVersion(int storedVersion) {
        return new IllegalStateException("floci-gcp state schema version " + storedVersion
                + " is incompatible with this emulator (supported version " + CURRENT_SCHEMA_VERSION + "). "
                + "State was not changed. To reset local state, stop the emulator and remove "
                + stateDirectory + " before starting it again.");
    }

    private record StateMetadata(int schemaVersion) {
    }
}
