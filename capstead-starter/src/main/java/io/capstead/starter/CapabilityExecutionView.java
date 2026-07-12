package io.capstead.starter;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A serialization-friendly view of a {@link CapabilityExecution} for the actuator endpoints.
 *
 * <p>{@code CapabilityExecution} uses fluent accessors ({@code executionId()}), which Jackson does not
 * treat as bean properties — serializing it directly yields an empty bean (and a failure when
 * {@code FAIL_ON_EMPTY_BEANS} is enabled, as in the actuator's ObjectMapper). This record exposes the
 * same data as plain components (records serialize reliably), with timestamps rendered as ISO-8601
 * strings so no JSR-310 module is required.
 */
public record CapabilityExecutionView(
        String executionId,
        String parentExecutionId,
        String capabilityName,
        String version,
        String coordinates,
        String domain,
        String principal,
        String startedAt,
        String finishedAt,
        long durationMs,
        int retries,
        boolean success,
        String errorType,
        int modelInvocationCount,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCost,
        String model,
        List<ModelInvocationView> modelInvocations,
        String capturedInput,
        String capturedOutput) {

    /** A serialization-friendly view of a single {@link ModelInvocation}. */
    public record ModelInvocationView(
            String model,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            BigDecimal estimatedCost,
            String invokedAt) {

        static ModelInvocationView of(ModelInvocation invocation) {
            return new ModelInvocationView(
                    invocation.model(),
                    invocation.inputTokens(),
                    invocation.outputTokens(),
                    invocation.totalTokens(),
                    invocation.estimatedCost(),
                    text(invocation.invokedAt()));
        }
    }

    /** Maps a {@link CapabilityExecution} to its view. */
    public static CapabilityExecutionView of(CapabilityExecution execution) {
        return new CapabilityExecutionView(
                execution.executionId(),
                execution.parentExecutionId(),
                execution.capabilityName(),
                execution.version(),
                execution.coordinates(),
                execution.domain(),
                execution.principal(),
                text(execution.startedAt()),
                text(execution.finishedAt()),
                execution.durationMs(),
                execution.retries(),
                execution.success(),
                execution.errorType(),
                execution.modelInvocationCount(),
                execution.inputTokens(),
                execution.outputTokens(),
                execution.totalTokens(),
                execution.estimatedCost(),
                execution.model(),
                execution.modelInvocations().stream().map(ModelInvocationView::of).toList(),
                execution.capturedInput(),
                execution.capturedOutput());
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
