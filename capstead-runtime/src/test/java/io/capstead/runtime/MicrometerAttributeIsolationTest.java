package io.capstead.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.core.CapabilityExecution;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ATTRIBUTES ARE NOT METRIC DIMENSIONS, asserted rather than documented.
 *
 * <p>This is the trap the attribute design exists to avoid. An attribute is allowed to be high-cardinality —
 * a run identifier, a source revision, a fixture version. A Micrometer tag is not: every distinct value
 * creates a new time series, so promoting one attribute to a tag can multiply a Prometheus instance's
 * series count by the number of runs and take the metrics backend down.
 *
 * <p>The separation is structural — {@link CapabilityMetrics} builds a fixed four-tag set from explicit
 * fields and never sees the attribute map. The value of this test is that it converts a future mistake from
 * a production incident into a red build: anyone who wires attributes into the tag set fails here.
 */
@DisplayName("Attributes never become Micrometer tags")
class MicrometerAttributeIsolationTest {

    private static final Set<String> THE_ONLY_TAGS = Set.of("capability", "version", "domain", "outcome");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void reset() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(ExecutionAttributeRegistry.empty());
    }

    private CapabilityExecution executionCarryingAttributes() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(new ExecutionAttributeRegistry(
                Set.of("run.id", "evidence.sourceRevision")));
        CapabilityExecutionContext.begin(CapabilityExecution.builder("Answer Question", "1"));

        // Exactly the shape that would be catastrophic as a tag: unique per execution.
        CapabilityExecutionContext.recordAttribute("run.id", "run-" + java.util.UUID.randomUUID());
        CapabilityExecutionContext.recordAttribute("evidence.sourceRevision", "17");

        return CapabilityExecutionContext.current().orElseThrow()
                .domain("Orders")
                .success(true)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .durationMs(5)
                .build();
    }

    @Test
    @DisplayName("the timer carries the four fixed tags and nothing derived from an attribute")
    void attributesDoNotReachTheTimer() {
        CapabilityExecution execution = executionCarryingAttributes();
        assertThat(execution.attributes())
                .as("the execution really is carrying attributes, or this test proves nothing")
                .hasSize(2);

        new MicrometerCapabilityRecorder(new CapabilityMetrics(registry)).record(execution);

        List<Meter> meters = List.copyOf(registry.getMeters());
        assertThat(meters).as("the recorder emitted a meter").isNotEmpty();

        for (Meter meter : meters) {
            Set<String> tagKeys = meter.getId().getTags().stream().map(Tag::getKey)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(tagKeys)
                    .as("tag keys on %s", meter.getId().getName())
                    .isEqualTo(THE_ONLY_TAGS);

            // Belt and braces: no tag VALUE smuggles an attribute value through either, which a helpful
            // future refactor could do without adding a new key.
            for (Tag tag : meter.getId().getTags()) {
                assertThat(execution.attributes().values())
                        .as("tag %s=%s must not carry an attribute value", tag.getKey(), tag.getValue())
                        .doesNotContain(tag.getValue());
            }
        }
    }
}
