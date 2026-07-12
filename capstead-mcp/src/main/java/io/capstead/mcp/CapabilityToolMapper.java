package io.capstead.mcp;

import io.capstead.core.CapabilityMetadata;
import io.capstead.runtime.CapabilityDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a governed {@link CapabilityDescriptor} onto an MCP {@link CapabilityTool} definition.
 *
 * <p>Produces a deterministic, MCP-safe tool name from the capability's {@code name@version}
 * coordinates and derives a JSON Schema for the business parameters from the method signature.
 * Capstead's governance metadata is attached so it travels with the tool into the MCP ecosystem.
 */
public class CapabilityToolMapper {

    /** Deterministic MCP-safe tool identifier, e.g. {@code "Generate Lesson"} v{@code 2} → {@code generate_lesson_v2}. */
    public String toolName(CapabilityMetadata metadata) {
        return sanitize(metadata.name()) + "_v" + sanitize(metadata.version());
    }

    /** Renders the full MCP tool definition for a discovered capability. */
    public CapabilityTool toTool(CapabilityDescriptor descriptor) {
        CapabilityMetadata metadata = descriptor.metadata();
        return new CapabilityTool(
                toolName(metadata),
                metadata.name(),
                metadata.description(),
                inputSchema(descriptor),
                governance(metadata));
    }

    private Map<String, Object> inputSchema(CapabilityDescriptor descriptor) {
        String[] names = descriptor.parameterNames();
        Class<?>[] types = descriptor.method().getParameterTypes();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", jsonType(types[i]));
            properties.put(names[i], property);
            required.add(names[i]);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> governance(CapabilityMetadata metadata) {
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("coordinates", metadata.coordinates());
        governance.put("domain", metadata.domain());
        governance.put("owner", metadata.owner());
        governance.put("version", metadata.version());
        governance.put("tags", metadata.tags());
        return governance;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class
                || type == Integer.class || type == Long.class || type == Short.class || type == Byte.class) {
            return "integer";
        }
        if (type == double.class || type == float.class || type == Double.class || type == Float.class
                || Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type.isArray() || Iterable.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }

    private static String sanitize(String raw) {
        String cleaned = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isEmpty() ? "capability" : cleaned;
    }
}
