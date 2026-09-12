package io.capstead.platform.expectation;

import java.time.Instant;
import java.util.Map;

/**
 * A declared, versioned execution contract: that a capability requires a given
 * child operation (Phase A: a tool) whenever its condition holds.
 *
 * <p>Expectations are DECLARED (via {@code ConfiguredExpectationSource}), never inferred from the
 * {@code CapabilityToolCatalog}. The catalog participates only through
 * {@code ToolIdentityResolver}, which validates that {@code expectedOperation} resolves to a
 * registered capability/tool and rejects unknown ones.
 *
 * <p>Conditions match against the governed {@code ExecutionAttributes} of the observed execution
 * (e.g. {@code request.category = ORDER_STATUS}). An expectation whose condition is not met is not
 * applicable — the execution produces no comparison and no rule evaluation.
 *
 * @param expectationId     stable identifier, e.g. {@code EXP-order-agent-lookup_order}
 * @param capabilityName    the parent capability (0.9.0 {@code CapabilityMetadata.name()})
 * @param version           the parent capability version the expectation applies to, or {@code null}
 *                          for all versions
 * @param expectedOperation the expected child operation identity
 * @param expectedType      Phase A: always {@code TOOL}
 * @param required          Phase A: always {@code true}
 * @param condition         attribute name/value pairs that must hold for this expectation to apply
 * @param revision          registry revision this expectation was registered at (monotonic)
 * @param active
 * @param createdAt
 */
public record ExecutionExpectation(
        String expectationId,
        String capabilityName,
        String version,
        String expectedOperation,
        String expectedType,
        boolean required,
        Map<String, String> condition,
        long revision,
        boolean active,
        Instant createdAt) {

    public ExecutionExpectation {
        if (expectationId == null || expectationId.isBlank())
            throw new IllegalArgumentException("expectationId must not be blank");
        if (capabilityName == null || capabilityName.isBlank())
            throw new IllegalArgumentException("capabilityName must not be blank");
        if (expectedOperation == null || expectedOperation.isBlank())
            throw new IllegalArgumentException("expectedOperation must not be blank");
        condition = condition == null ? Map.of() : Map.copyOf(condition);
    }

    /** Whether this expectation's condition holds for the given execution attributes. */
    public boolean isApplicableTo(Map<String, String> attributes) {
        for (var entry : condition.entrySet()) {
            String actual = attributes.get(entry.getKey());
            if (!entry.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }
}