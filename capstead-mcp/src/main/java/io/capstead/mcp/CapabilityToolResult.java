package io.capstead.mcp;

import java.util.List;

/**
 * The result of invoking a capability tool, shaped after the MCP {@code tools/call} result:
 * a list of content blocks plus an {@code isError} flag.
 *
 * @param content the result content blocks (Capstead emits a single text block)
 * @param isError whether the invocation failed (budget denial, bad arguments, or a thrown error)
 */
public record CapabilityToolResult(List<Content> content, boolean isError) {

    /** A single MCP text content block. */
    public record Content(String type, String text) {
    }

    /** A successful result carrying the capability's return value rendered as text. */
    public static CapabilityToolResult ok(String text) {
        return new CapabilityToolResult(List.of(new Content("text", text)), false);
    }

    /** A failed result carrying an error message. */
    public static CapabilityToolResult error(String text) {
        return new CapabilityToolResult(List.of(new Content("text", text)), true);
    }
}
