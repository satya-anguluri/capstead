package io.capstead.mcp.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.capstead.mcp.CapabilityTool;
import io.capstead.mcp.CapabilityToolInvoker;
import io.capstead.mcp.CapabilityToolResult;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * Adapts a single governed capability to a Spring AI {@link ToolCallback}, so it can be served by
 * any Spring AI MCP server transport (STDIO, SSE, Streamable-HTTP).
 *
 * <p>The tool definition carries the capability's name, description and JSON input schema. On
 * invocation the JSON tool input is bound to the capability's parameters and dispatched through the
 * governing proxy via {@link CapabilityToolInvoker}, so budget enforcement and execution recording
 * apply exactly as for a direct call.
 */
public class CapabilityToolCallback implements ToolCallback {

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };

    private final String toolName;
    private final ToolDefinition toolDefinition;
    private final CapabilityToolInvoker invoker;
    private final ObjectMapper objectMapper;

    public CapabilityToolCallback(CapabilityTool tool, CapabilityToolInvoker invoker, ObjectMapper objectMapper) {
        this.toolName = tool.name();
        this.invoker = invoker;
        this.objectMapper = objectMapper;
        this.toolDefinition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(writeSchema(tool.inputSchema(), objectMapper))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> arguments = parseArguments(toolInput);
        CapabilityToolResult result = invoker.call(toolName, arguments);
        return result.content().isEmpty() ? "" : result.content().get(0).text();
    }

    private Map<String, Object> parseArguments(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(toolInput, ARGUMENTS_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Tool input for '" + toolName + "' is not a JSON object", ex);
        }
    }

    private static String writeSchema(Map<String, Object> inputSchema, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(inputSchema);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize input schema for MCP tool", ex);
        }
    }
}
