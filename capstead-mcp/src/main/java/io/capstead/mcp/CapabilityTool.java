package io.capstead.mcp;

import java.util.Map;

/**
 * A single governed capability rendered as a Model Context Protocol (MCP) tool definition.
 *
 * <p>The {@code name}, {@code description} and {@code inputSchema} fields mirror the MCP
 * {@code tools/list} tool shape, so this can be surfaced directly to an MCP client. The
 * {@code governance} field carries Capstead's differentiator — the ownership, version and domain
 * metadata that plain MCP tools lack — and maps naturally onto the MCP {@code _meta} extension.
 *
 * @param name        MCP-safe tool identifier derived from the capability {@code name@version}
 * @param title       the human-readable business name of the capability
 * @param description business description surfaced to the MCP client
 * @param inputSchema JSON Schema (object) describing the capability's business parameters
 * @param governance  Capstead governance metadata (coordinates, domain, owner, version, tags)
 */
public record CapabilityTool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> governance) {
}
