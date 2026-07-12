package io.capstead.mcp;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator endpoint that publishes Capstead's capabilities as MCP tools.
 *
 * <ul>
 *   <li>{@code GET /actuator/capabilitymcp} — the {@code tools/list} view of every governed capability.</li>
 *   <li>{@code GET /actuator/capabilitymcp/{name}} — a single tool definition.</li>
 *   <li>{@code POST /actuator/capabilitymcp/{name}} with body {@code {"arguments": { ... }}} —
 *       a {@code tools/call} that runs the capability through its governing proxy.</li>
 * </ul>
 *
 * <p>This is a management endpoint and therefore subject to the application's actuator access
 * controls; the {@code POST} form actually invokes business logic, so expose it only where that is
 * intended.
 */
@Endpoint(id = "capabilitymcp")
public class CapabilityMcpEndpoint {

    private final CapabilityToolCatalog catalog;
    private final CapabilityToolInvoker invoker;

    public CapabilityMcpEndpoint(CapabilityToolCatalog catalog, CapabilityToolInvoker invoker) {
        this.catalog = catalog;
        this.invoker = invoker;
    }

    /** {@code GET /actuator/capabilitymcp} — the MCP {@code tools/list} result. */
    @ReadOperation
    public Map<String, Object> tools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", catalog.tools());
        return result;
    }

    /** {@code GET /actuator/capabilitymcp/{name}} — a single MCP tool definition. */
    @ReadOperation
    public CapabilityTool tool(@Selector String name) {
        return catalog.find(name).orElse(null);
    }

    /** {@code POST /actuator/capabilitymcp/{name}} — an MCP {@code tools/call} against the capability. */
    @WriteOperation
    public CapabilityToolResult call(@Selector String name, @Nullable Map<String, Object> arguments) {
        return invoker.call(name, arguments);
    }
}
