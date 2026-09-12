package io.capstead.platform.expectation;

import io.capstead.mcp.CapabilityToolCatalog;
import io.capstead.mcp.CapabilityToolMapper;

import java.util.Optional;

/**
 * Validates expectation references against the 0.9.0 tool identity space, and resolves canonical
 * tool identities for capability coordinates.
 *
 * <p>Deliberately narrow: the catalog can say that {@code lookup_order} EXISTS and what its
 * canonical name is. It cannot say that {@code OrderAgent} must call it when
 * {@code request.category = ORDER_STATUS} — that relationship is declared by
 * {@code ConfiguredExpectationSource}. This class therefore only:
 *
 * <ul>
 *   <li>resolves the canonical tool identity for a capability coordinate
 *       ({@code CapabilityToolMapper.toolName(metadata)}), and</li>
 *   <li>rejects expectations whose {@code expectedOperation} does not resolve to a registered
 *       capability (unknown tools).</li>
 * </ul>
 *
 * <p>It never creates expectations. Creating a required execution relationship from the catalog
 * alone would manufacture contracts that were never intended.
 */
public final class ToolIdentityResolver {

    private final CapabilityToolCatalog catalog;

    public ToolIdentityResolver(CapabilityToolCatalog catalog) {
        this.catalog = catalog;
    }

    /** The canonical tool identity for the given capability coordinate, if registered. */
    public Optional<ToolIdentity> resolve(String capabilityName, String version) {
        return catalog.descriptorFor(toolNameOf(capabilityName, version))
                .map(d -> new ToolIdentity(
                        toolNameOf(capabilityName, version),
                        d.metadata().name(),
                        d.metadata().version()));
    }

    /** Whether {@code expectedOperation} resolves to a registered capability/tool. */
    public boolean isKnownTool(String expectedOperation) {
        return catalog.descriptorFor(expectedOperation).isPresent();
    }

    private String toolNameOf(String capabilityName, String version) {
        // The mapper's deterministic convention: sanitize(name) + "_v" + sanitize(version).
        return new CapabilityToolMapper().toolName(
                new io.capstead.core.CapabilityMetadata(
                        capabilityName, null, null, null,
                        version == null ? "1" : version, null));
    }

    /** A validated tool identity as the platform refers to it. */
    public record ToolIdentity(String toolName, String capabilityName, String version) {}
}