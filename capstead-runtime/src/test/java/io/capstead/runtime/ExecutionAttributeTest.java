package io.capstead.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ExecutionAttributes;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Execution attributes: what gets recorded, and what gets refused.
 *
 * <p>The refusals are the point of the feature. An attribute map on the atom every dashboard, cost report
 * and budget derives from becomes a dumping ground unless the framework says no, so the tests that assert
 * "no" matter more here than the one that asserts "yes".
 */
@DisplayName("Execution attributes")
class ExecutionAttributeTest {

    private static final String ALLOWED = "policy.authorization.outcome";

    @BeforeEach
    void declareTheAllowList() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(
                new ExecutionAttributeRegistry(Set.of(ALLOWED, "evidence.state", "lab.fixture.version")));
        CapabilityExecutionContext.begin(CapabilityExecution.builder("Answer Question", "1"));
    }

    @AfterEach
    void reset() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(ExecutionAttributeRegistry.empty());
    }

    private CapabilityExecution finish() {
        return CapabilityExecutionContext.current().orElseThrow().build();
    }

    @Test
    @DisplayName("a declared attribute is readable from the recorded execution")
    void declaredAttributesAreRecorded() {
        CapabilityExecutionContext.recordAttribute(ALLOWED, "DENY");
        CapabilityExecutionContext.recordAttribute("evidence.state", "STALE_EVIDENCE");

        CapabilityExecution execution = finish();

        assertThat(execution.attribute(ALLOWED)).isEqualTo("DENY");
        assertThat(execution.attributes())
                .containsExactly(
                        org.assertj.core.data.MapEntry.entry(ALLOWED, "DENY"),
                        org.assertj.core.data.MapEntry.entry("evidence.state", "STALE_EVIDENCE"));
    }

    @Test
    @DisplayName("an undeclared name is not stored")
    void undeclaredNamesAreRejected() {
        CapabilityExecutionContext.recordAttribute("policy.invented.inahurry", "SOMETHING");

        assertThat(finish().attributes()).isEmpty();
    }

    @Test
    @DisplayName("declaring nothing allows nothing — an application that never opted in records nothing")
    void anEmptyRegistryAllowsNothing() {
        CapabilityExecutionContext.useRegistry(ExecutionAttributeRegistry.empty());

        CapabilityExecutionContext.recordAttribute(ALLOWED, "DENY");

        assertThat(finish().attributes()).isEmpty();
    }

    @Test
    @DisplayName("a name with no namespace is refused even if it is declared")
    void namesMustBeNamespaced() {
        assertThat(ExecutionAttributes.isValidName("outcome")).isFalse();
        assertThat(ExecutionAttributes.isValidName("policy.outcome")).isTrue();

        // A malformed name cannot be declared into existence either.
        assertThat(new ExecutionAttributeRegistry(Set.of("outcome")).allows("outcome")).isFalse();
    }

    /**
     * The four names the issue itself uses as examples. Worth pinning: an earlier version of the name rule
     * allowed only lower-case segments, which silently refused {@code evidence.sourceRevision} — a name in
     * the specification. The refusal was invisible except as a missing attribute.
     */
    @Test
    @DisplayName("the documented example names are all valid, camelCase included")
    void theDocumentedNamesAreValid() {
        assertThat(ExecutionAttributes.isValidName("lab.fixture.version")).isTrue();
        assertThat(ExecutionAttributes.isValidName("evidence.state")).isTrue();
        assertThat(ExecutionAttributes.isValidName("evidence.sourceRevision")).isTrue();
        assertThat(ExecutionAttributes.isValidName("policy.authorization.outcome")).isTrue();

        // Still not a namespace: an upper-case root reads as a type name.
        assertThat(ExecutionAttributes.isValidName("Evidence.state")).isFalse();
        // And a dot cannot open or close a name, or double up.
        assertThat(ExecutionAttributes.isValidName(".evidence.state")).isFalse();
        assertThat(ExecutionAttributes.isValidName("evidence.state.")).isFalse();
        assertThat(ExecutionAttributes.isValidName("evidence..state")).isFalse();
    }

    @Test
    @DisplayName("an over-length value is not stored, and is not truncated into a plausible-looking one")
    void overLongValuesAreRefusedRatherThanTruncated() {
        String tooLong = "x".repeat(ExecutionAttributes.MAX_VALUE_LENGTH + 1);

        CapabilityExecutionContext.recordAttribute(ALLOWED, tooLong);

        assertThat(finish().attributes()).isEmpty();
    }

    @Test
    @DisplayName("a value at exactly the limit is stored")
    void theLimitIsInclusive() {
        String atLimit = "x".repeat(ExecutionAttributes.MAX_VALUE_LENGTH);

        CapabilityExecutionContext.recordAttribute(ALLOWED, atLimit);

        assertThat(finish().attribute(ALLOWED)).hasSize(ExecutionAttributes.MAX_VALUE_LENGTH);
    }

    @Test
    @DisplayName("one execution cannot grow without bound")
    void thereIsACeilingPerExecution() {
        Set<String> many = new java.util.LinkedHashSet<>();
        for (int i = 0; i <= ExecutionAttributes.MAX_PER_EXECUTION; i++) {
            many.add("bulk.attr" + i);
        }
        CapabilityExecutionContext.useRegistry(new ExecutionAttributeRegistry(many));

        for (String name : many) {
            CapabilityExecutionContext.recordAttribute(name, "v");
        }

        assertThat(finish().attributes()).hasSize(ExecutionAttributes.MAX_PER_EXECUTION);
    }

    @Test
    @DisplayName("re-setting a name overwrites it and does not consume the ceiling twice")
    void resettingOverwrites() {
        CapabilityExecutionContext.recordAttribute(ALLOWED, "ALLOW");
        CapabilityExecutionContext.recordAttribute(ALLOWED, "DENY");

        assertThat(finish().attributes()).hasSize(1);
        assertThat(finish().attribute(ALLOWED)).isEqualTo("DENY");
    }

    @Test
    @DisplayName("recording never throws, whatever it is handed")
    void enrichmentNeverBreaksTheBusinessMethod() {
        assertThatCode(() -> {
            CapabilityExecutionContext.recordAttribute(null, "v");
            CapabilityExecutionContext.recordAttribute(ALLOWED, null);
            CapabilityExecutionContext.recordAttribute("", "");
            CapabilityExecutionContext.recordAttribute("Policy.UPPER", "v");
        }).doesNotThrowAnyException();

        assertThat(finish().attributes()).isEmpty();
    }

    @Test
    @DisplayName("with no execution in flight it is a no-op, like every other enrichment call")
    void noOpOutsideAnExecution() {
        CapabilityExecutionContext.clear();

        assertThatCode(() -> CapabilityExecutionContext.recordAttribute(ALLOWED, "DENY"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an execution that sets no attributes has an empty map, not null")
    void existingConsumersAreUnaffected() {
        CapabilityExecution execution = finish();

        assertThat(execution.attributes()).isNotNull().isEmpty();
        assertThat(execution.attribute("anything")).isNull();
    }
}
