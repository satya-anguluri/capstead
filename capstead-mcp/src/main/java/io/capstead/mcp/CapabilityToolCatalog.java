package io.capstead.mcp;

import io.capstead.runtime.CapabilityDescriptor;
import io.capstead.runtime.CapabilityRegistry;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only view that renders every registered capability as an MCP {@link CapabilityTool}.
 *
 * <p>Backed live by the {@link CapabilityRegistry}, so it reflects whatever discovery registered at
 * startup. This is the {@code tools/list} data source for MCP export; invocation is handled
 * separately by {@link CapabilityToolInvoker} so the read surface stays side-effect free.
 */
public class CapabilityToolCatalog {

    private final CapabilityRegistry registry;
    private final CapabilityToolMapper mapper;

    public CapabilityToolCatalog(CapabilityRegistry registry, CapabilityToolMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    /** Every governed capability rendered as an MCP tool, in registration order. */
    public List<CapabilityTool> tools() {
        return registry.all().stream()
                .map(mapper::toTool)
                .collect(Collectors.toList());
    }

    /** The MCP tool definition for a given tool name, if one is registered. */
    public Optional<CapabilityTool> find(String toolName) {
        return descriptorFor(toolName).map(mapper::toTool);
    }

    /** The underlying descriptor for a tool name, used by the invoker to dispatch a call. */
    public Optional<CapabilityDescriptor> descriptorFor(String toolName) {
        return registry.all().stream()
                .filter(descriptor -> mapper.toolName(descriptor.metadata()).equals(toolName))
                .findFirst();
    }
}
