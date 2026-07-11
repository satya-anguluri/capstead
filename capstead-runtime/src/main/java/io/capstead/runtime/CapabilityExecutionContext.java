package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Thread-local access to the capability execution currently in flight.
 *
 * <p>This is Capstead's enrichment seam. Because Capstead does not execute the model, it cannot
 * observe token usage, model name or cost directly — those are supplied by whoever <em>does</em>
 * make the call. A Spring AI observation handler (or, if desired, the capability body itself) can
 * attach that data to the enclosing execution without owning its lifecycle:
 *
 * <pre>{@code
 * CapabilityExecutionContext.recordModel("claude-sonnet");
 * CapabilityExecutionContext.recordTokens(1240, 2980);
 * CapabilityExecutionContext.recordCost(new BigDecimal("0.023"));
 * }</pre>
 *
 * <p>The {@code begin}/{@code clear} lifecycle is managed by {@link CapabilityMethodInterceptor};
 * enrichment calls are no-ops when no execution is active.
 */
public final class CapabilityExecutionContext {

    private static final ThreadLocal<CapabilityExecution.Builder> CURRENT = new ThreadLocal<>();

    private CapabilityExecutionContext() {
    }

    /** Marks the start of an execution on the current thread. */
    public static void begin(CapabilityExecution.Builder builder) {
        CURRENT.set(builder);
    }

    /** The in-flight execution builder, if any. */
    public static Optional<CapabilityExecution.Builder> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Clears the current execution from the thread. */
    public static void clear() {
        CURRENT.remove();
    }

    /** Enriches the current execution with the model used. */
    public static void recordModel(String model) {
        current().ifPresent(builder -> builder.model(model));
    }

    /** Enriches the current execution with token usage. */
    public static void recordTokens(int inputTokens, int outputTokens) {
        current().ifPresent(builder -> builder.inputTokens(inputTokens).outputTokens(outputTokens));
    }

    /** Enriches the current execution with an estimated cost. */
    public static void recordCost(BigDecimal estimatedCost) {
        current().ifPresent(builder -> builder.estimatedCost(estimatedCost));
    }

    /** Enriches the current execution with a retry count. */
    public static void recordRetries(int retries) {
        current().ifPresent(builder -> builder.retries(retries));
    }
}
