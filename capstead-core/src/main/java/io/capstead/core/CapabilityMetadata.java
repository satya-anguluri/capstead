package io.capstead.core;

import java.util.List;

/**
 * Immutable governance metadata describing a registered capability.
 *
 * <p>This is the public read model Capstead exposes through discovery endpoints
 * (e.g. {@code /actuator/capabilities}), the dashboard, and cross-service catalog federation.
 * It is intentionally transport- and engine-agnostic: nothing here references how the
 * capability is executed.
 */
public record CapabilityMetadata(
        String name,
        String description,
        String domain,
        String owner,
        String version,
        List<String> tags) {

    public CapabilityMetadata {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Stable identity of a capability across versions: {@code name@version}. */
    public String coordinates() {
        return name + "@" + version;
    }
}
