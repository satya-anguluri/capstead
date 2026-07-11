package io.capstead.starter;

import io.capstead.core.CapabilityMetadata;
import io.capstead.runtime.CapabilityCatalog;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.List;

/**
 * Actuator endpoint exposing the capability catalog at {@code GET /actuator/capabilities}.
 *
 * <p>This is Capstead's primary discovery surface: it returns governance metadata only, so it is
 * safe to expose and can be consumed by dashboards or by other services performing cross-service
 * catalog federation.
 */
@Endpoint(id = "capabilities")
public class CapabilitiesEndpoint {

    private final CapabilityCatalog catalog;

    public CapabilitiesEndpoint(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    /** {@code GET /actuator/capabilities} — every registered capability's metadata. */
    @ReadOperation
    public List<CapabilityMetadata> capabilities() {
        return catalog.capabilities();
    }

    /** {@code GET /actuator/capabilities/{name}} — all versions of one capability. */
    @ReadOperation
    public List<CapabilityMetadata> byName(@Selector String name) {
        return catalog.versionsOf(name);
    }
}
