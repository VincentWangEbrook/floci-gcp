package io.floci.gcp.services.cloudrun;

import io.floci.gcp.core.common.GcpException;

import java.util.List;

/** Resolves the only container image references Cloud Run local execution may use. */
final class CloudRunImageMap {

    private CloudRunImageMap() {
    }

    static String resolve(String requestedImage, List<String> configuredMappings) {
        for (String mapping : configuredMappings) {
            int separator = mapping.indexOf('=');
            if (separator > 0 && requestedImage.equals(mapping.substring(0, separator))) {
                String mappedImage = mapping.substring(separator + 1).trim();
                if (!mappedImage.isEmpty()) {
                    return mappedImage;
                }
            }
        }
        throw GcpException.failedPrecondition(
                "Cloud Run local execution requires an exact configured image map for: " + requestedImage);
    }
}
