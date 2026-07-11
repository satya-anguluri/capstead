package io.capstead.runtime;

import io.capstead.core.CapabilityMetadata;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only view over the {@link CapabilityRegistry}, exposing only governance metadata.
 *
 * <p>This is the data source for Capstead's discovery surfaces — the
 * {@code /actuator/capabilities} endpoint, the dashboard, and cross-service catalog federation.
 * It deliberately exposes {@link CapabilityMetadata} only, never bean or method handles, so the
 * public catalog stays transport- and engine-agnostic.
 */
public final class CapabilityCatalog {

    private final CapabilityRegistry registry;

    public CapabilityCatalog(CapabilityRegistry registry) {
        this.registry = registry;
    }

    /** All registered capabilities' metadata, in registration order. */
    public List<CapabilityMetadata> capabilities() {
        return registry.all().stream()
                .map(CapabilityDescriptor::metadata)
                .collect(Collectors.toList());
    }

    /** Metadata for all versions of a capability sharing the given business name. */
    public List<CapabilityMetadata> versionsOf(String name) {
        return registry.all().stream()
                .map(CapabilityDescriptor::metadata)
                .filter(metadata -> metadata.name().equals(name))
                .collect(Collectors.toList());
    }
}
