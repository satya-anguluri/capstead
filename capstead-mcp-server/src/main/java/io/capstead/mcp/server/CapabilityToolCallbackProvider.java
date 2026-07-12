package io.capstead.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.capstead.mcp.CapabilityToolCatalog;
import io.capstead.mcp.CapabilityToolInvoker;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Spring AI {@link ToolCallbackProvider} that exposes every governed capability as a tool.
 *
 * <p>An MCP server Boot starter (for example {@code spring-ai-starter-mcp-server}) automatically
 * discovers {@code ToolCallbackProvider} beans and publishes their tools over the configured MCP
 * transport. Registering this provider is therefore all an application needs to serve its Capstead
 * capabilities — with their governance intact — to any MCP client.
 */
public class CapabilityToolCallbackProvider implements ToolCallbackProvider {

    private final CapabilityToolCatalog catalog;
    private final CapabilityToolInvoker invoker;
    private final ObjectMapper objectMapper;

    public CapabilityToolCallbackProvider(CapabilityToolCatalog catalog,
                                          CapabilityToolInvoker invoker,
                                          ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.invoker = invoker;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return catalog.tools().stream()
                .map(tool -> new CapabilityToolCallback(tool, invoker, objectMapper))
                .toArray(ToolCallback[]::new);
    }
}
