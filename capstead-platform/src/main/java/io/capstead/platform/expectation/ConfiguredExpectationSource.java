package io.capstead.platform.expectation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The authoritative Phase-A source of execution contracts. Declares parent-capability →
 * required-tool relationships and their conditions (application.yml / properties).
 *
 * <p>The {@code CapabilityToolCatalog} does NOT create expectations: it cannot express
 * "OrderAgent MUST call lookup_order when request.category = ORDER_STATUS". It participates only
 * through {@link ToolIdentityResolver}, which validates tool identity and rejects unknown tools.
 *
 * <p>Declaration shape (properties):
 *
 * <pre>
 * capstead.platform.expectations[0].capability = order-agent
 * capstead.platform.expectations[0].version = 1          # optional, omitted = all versions
 * capstead.platform.expectations[0].expected-operation = lookup_order_v1
 * capstead.platform.expectations[0].required = true
 * capstead.platform.expectations[0].condition.request-category = ORDER_STATUS
 * </pre>
 */
public final class ConfiguredExpectationSource {

    /** One declared expectation, as bound from configuration. */
    public record Declaration(
            String capability,
            String version,                // nullable = all versions
            String expectedOperation,
            boolean required,
            Map<String, String> condition) {}

    private final List<Declaration> declarations;
    private final ToolIdentityResolver identityResolver;

    public ConfiguredExpectationSource(List<Declaration> declarations,
                                       ToolIdentityResolver identityResolver) {
        this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
        this.identityResolver = identityResolver;
    }

    /**
     * Converts declarations into validated {@link ExecutionExpectation}s.
     *
     * <p>Unknown tools FAIL FAST: an expectation referencing an operation the catalog does not
     * know is a configuration error and stops startup with the offending declaration in the
     * message, rather than being dropped silently.
     */
    public List<ExecutionExpectation> expectations() {
        return declarations.stream().map(this::materialize).toList();
    }

    private ExecutionExpectation materialize(Declaration d) {
        if (!identityResolver.isKnownTool(d.expectedOperation())) {
            throw new IllegalStateException(
                    "Capstead platform expectation references an unknown tool: capability='"
                            + d.capability() + "' expectedOperation='" + d.expectedOperation()
                            + "'. Declare the child capability with @Capability (or adjust the "
                            + "expected-operation value) so the tool identity resolves.");
        }
        long revision = 0;
        return new ExecutionExpectation(
                expectationId(d.capability(), d.expectedOperation()),
                d.capability(),
                d.version(),
                d.expectedOperation(),
                "TOOL",
                d.required(),
                d.condition(),
                revision,
                true,
                Instant.now());
    }

    private static String expectationId(String capability, String operation) {
        return "EXP-" + capability + "-" + operation;
    }
}