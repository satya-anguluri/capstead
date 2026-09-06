package io.capstead.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.core.CapabilityExecution;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Attributes reach the actuator view.
 *
 * <p>They were recorded and persisted for a while before they were exposed, which meant a developer at
 * {@code /actuator/capabilityExecutions} could not see the evidence an execution had recorded — present in
 * the store, invisible everywhere a human looks. This asserts the mapping so that cannot regress quietly.
 */
@DisplayName("CapabilityExecutionView attributes")
class CapabilityExecutionViewAttributeTest {

    private static CapabilityExecution.Builder execution() {
        Instant now = Instant.now();
        return CapabilityExecution.builder("Answer Question", "1")
                .executionId("exec-1")
                .startedAt(now)
                .finishedAt(now)
                .durationMs(1)
                .success(true);
    }

    @Test
    @DisplayName("recorded attributes appear in the serialized view")
    void attributesAreExposed() {
        CapabilityExecutionView view = CapabilityExecutionView.of(execution()
                .attribute("evidence.state", "STALE_EVIDENCE")
                .attribute("payment.idempotencyKey", "idem-ORD-1001")
                .build());

        assertThat(view.attributes())
                .containsEntry("evidence.state", "STALE_EVIDENCE")
                .containsEntry("payment.idempotencyKey", "idem-ORD-1001");
    }

    @Test
    @DisplayName("an execution with none gives an empty map, never null")
    void emptyRatherThanNull() {
        assertThat(CapabilityExecutionView.of(execution().build()).attributes())
                .as("an absent key and an empty object mean the same thing to a consumer; one is a null check")
                .isNotNull()
                .isEmpty();
    }
}
