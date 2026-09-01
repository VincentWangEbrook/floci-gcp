package io.floci.gcp.services.cloudrun;

import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.docker.ContainerSpec;

import java.util.List;
import java.util.Set;

/** Security boundary for containers launched to emulate Cloud Run revisions. */
final class CloudRunContainerSecurityPolicy {

    private static final Set<String> GCP_CREDENTIAL_ENVIRONMENT_VARIABLES = Set.of(
            "GOOGLE_APPLICATION_CREDENTIALS",
            "GOOGLE_CLOUD_CREDENTIALS",
            "GOOGLE_GHA_CREDS_PATH",
            "GOOGLE_OAUTH_ACCESS_TOKEN");

    private CloudRunContainerSecurityPolicy() {
    }

    static void validate(ContainerSpec spec) {
        if (spec.privileged()) {
            throw rejected("privileged containers");
        }
        if (spec.cgroupnsMode() != null && !spec.cgroupnsMode().isBlank()) {
            throw rejected("a host cgroup namespace");
        }
        if (spec.binds() != null && !spec.binds().isEmpty()) {
            throw rejected("host bind mounts");
        }
        rejectUnsafeMounts(spec.mounts());
        rejectCredentialEnvironment(spec.env());
    }

    private static void rejectUnsafeMounts(List<Mount> mounts) {
        if (mounts == null) {
            return;
        }
        for (Mount mount : mounts) {
            if (mount.getType() == MountType.BIND) {
                throw rejected("host bind mounts");
            }
            String source = mount.getSource();
            String target = mount.getTarget();
            if (containsDockerSocket(source) || containsDockerSocket(target)) {
                throw rejected("Docker socket mounts");
            }
        }
    }

    private static void rejectCredentialEnvironment(List<String> environment) {
        if (environment == null) {
            return;
        }
        for (String entry : environment) {
            int separator = entry.indexOf('=');
            String key = separator >= 0 ? entry.substring(0, separator) : entry;
            if (GCP_CREDENTIAL_ENVIRONMENT_VARIABLES.contains(key)) {
                throw rejected("real GCP credential environment variables");
            }
        }
    }

    private static boolean containsDockerSocket(String value) {
        return value != null && value.contains("docker.sock");
    }

    private static GcpException rejected(String capability) {
        return GcpException.invalidArgument("Cloud Run local execution rejects " + capability);
    }
}
