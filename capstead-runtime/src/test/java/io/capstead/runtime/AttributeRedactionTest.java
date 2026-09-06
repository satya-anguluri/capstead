package io.capstead.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ExecutionAttributes;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Redaction reaches attribute values, not only captured input and output.
 *
 * <p>Attributes are the second path into durable storage and were not covered when they were added: an
 * application recording a reason code that happened to carry a token stored it raw, however carefully it had
 * configured a redactor. The safety of the captured-I/O path rests on capture being off by default;
 * attributes have no such default, because recording them is the point.
 */
@DisplayName("Attribute redaction")
class AttributeRedactionTest {

    private static final String NAME = "policy.authorization.reason";

    @BeforeEach
    void declare() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(new ExecutionAttributeRegistry(Set.of(NAME, "evidence.state")));
    }

    @AfterEach
    void reset() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(ExecutionAttributeRegistry.empty());
    }

    private static CapabilityExecution.Builder with(String name, String value) {
        CapabilityExecution.Builder builder = CapabilityExecution.builder("Answer Question", "1");
        CapabilityExecutionContext.begin(builder);
        CapabilityExecutionContext.recordAttribute(name, value);
        return builder;
    }

    @Test
    @DisplayName("a value carrying a secret is rewritten by the redactor")
    void valuesPassThroughTheRedactor() {
        CapabilityExecution.Builder builder = with(NAME, "denied for token sk-abc123");

        CapabilityExecution execution = builder
                .sanitizeAttributes(v -> v.replaceAll("sk-\\w+", "[redacted]"))
                .build();

        assertThat(execution.attribute(NAME)).isEqualTo("denied for token [redacted]");
        assertThat(execution.attribute(NAME)).doesNotContain("sk-abc123");
    }

    @Test
    @DisplayName("names and order are preserved — redaction rewrites values only")
    void namesAndOrderSurvive() {
        CapabilityExecution.Builder builder = CapabilityExecution.builder("Answer Question", "1");
        CapabilityExecutionContext.begin(builder);
        CapabilityExecutionContext.recordAttribute(NAME, "one");
        CapabilityExecutionContext.recordAttribute("evidence.state", "two");

        CapabilityExecution execution = builder.sanitizeAttributes(v -> v.toUpperCase()).build();

        assertThat(execution.attributes()).containsExactly(
                org.assertj.core.data.MapEntry.entry(NAME, "ONE"),
                org.assertj.core.data.MapEntry.entry("evidence.state", "TWO"));
    }

    /**
     * The safe failure for redaction specifically. A value that could not be cleaned must not be stored as
     * it was, and a placeholder would claim the cleaning succeeded when it did not.
     */
    @Test
    @DisplayName("a redactor returning null removes the attribute rather than keeping the raw value")
    void unredactableValuesAreDropped() {
        CapabilityExecution execution = with(NAME, "unsafe").sanitizeAttributes(v -> null).build();

        assertThat(execution.attributes()).isEmpty();
    }

    @Test
    @DisplayName("a redactor that lengthens a value cannot get it dropped by the per-execution ceiling")
    void rewritingIsNotResetting() {
        CapabilityExecution execution = with(NAME, "x")
                .sanitizeAttributes(v -> v + " [redacted by policy]")
                .build();

        assertThat(execution.attribute(NAME)).isEqualTo("x [redacted by policy]");
    }

    @Test
    @DisplayName("a redactor that overflows the value limit drops the attribute rather than storing it over-length")
    void overflowIsStillRefused() {
        CapabilityExecution execution = with(NAME, "x")
                .sanitizeAttributes(v -> "y".repeat(ExecutionAttributes.MAX_VALUE_LENGTH + 1))
                .build();

        assertThat(execution.attributes()).isEmpty();
    }

    @Test
    @DisplayName("no redactor configured leaves values exactly as recorded")
    void theNoOpPathIsUnchanged() {
        CapabilityExecution execution = with(NAME, "ACCESS_REVOKED")
                .sanitizeAttributes(new NoOpCapabilityDataRedactor()::redact)
                .build();

        assertThat(execution.attribute(NAME)).isEqualTo("ACCESS_REVOKED");
    }
}
