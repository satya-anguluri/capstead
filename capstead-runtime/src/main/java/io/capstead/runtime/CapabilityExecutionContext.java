package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local access to the capability execution(s) currently in flight.
 *
 * <p>This is Capstead's enrichment seam. Because Capstead does not execute the model, it cannot
 * observe token usage, model name or cost directly — those are supplied by whoever <em>does</em>
 * make the call:
 *
 * <pre>{@code
 * CapabilityExecutionContext.recordModelInvocation("claude-sonnet", 1240, 2980, new BigDecimal("0.023"));
 * }</pre>
 *
 * <p>Executions are held on a <strong>stack</strong>, so when one {@code @Capability} calls another
 * (a composed capability), the nested execution is automatically linked to its parent via
 * {@code parentExecutionId} — giving an execution tree with no workflow engine. The
 * {@code begin}/{@code clear} lifecycle is managed by {@link CapabilityMethodInterceptor}; enrichment
 * calls are no-ops when no execution is active.
 */
public final class CapabilityExecutionContext {

    private static final ThreadLocal<Deque<CapabilityExecution.Builder>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private CapabilityExecutionContext() {
    }

    /**
     * Marks the start of an execution on the current thread: assigns its {@code executionId} (if
     * unset) and links it to the enclosing execution's id, then pushes it onto the stack.
     */
    public static void begin(CapabilityExecution.Builder builder) {
        Deque<CapabilityExecution.Builder> stack = STACK.get();
        if (builder.executionId() == null) {
            builder.executionId(UUID.randomUUID().toString());
        }
        CapabilityExecution.Builder parent = stack.peek();
        if (parent != null && builder.parentExecutionId() == null) {
            builder.parentExecutionId(parent.executionId());
        }
        stack.push(builder);
    }

    /** The in-flight execution builder (top of the stack), if any. */
    public static Optional<CapabilityExecution.Builder> current() {
        return Optional.ofNullable(STACK.get().peek());
    }

    /** Ends the current execution on the thread by popping the stack. */
    public static void clear() {
        Deque<CapabilityExecution.Builder> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /** Records one model/LLM call against the current execution (the multi-call API). */
    public static void recordModelInvocation(String model, int inputTokens, int outputTokens, BigDecimal estimatedCost) {
        current().ifPresent(builder -> builder.addModelInvocation(
                new ModelInvocation(model, inputTokens, outputTokens, estimatedCost, Instant.now())));
    }

    /** Records the principal (caller) for the current execution. */
    public static void recordPrincipal(String principal) {
        current().ifPresent(builder -> builder.principal(principal));
    }

    /**
     * Enriches the current execution with the model used. Back-compat single-invocation API — for
     * multiple calls, prefer {@link #recordModelInvocation}.
     */
    public static void recordModel(String model) {
        current().ifPresent(builder -> builder.model(model));
    }

    /** Enriches the current execution with token usage (back-compat single-invocation API). */
    public static void recordTokens(int inputTokens, int outputTokens) {
        current().ifPresent(builder -> builder.inputTokens(inputTokens).outputTokens(outputTokens));
    }

    /** Enriches the current execution with an estimated cost (back-compat single-invocation API). */
    public static void recordCost(BigDecimal estimatedCost) {
        current().ifPresent(builder -> builder.estimatedCost(estimatedCost));
    }

    /** Enriches the current execution with a retry count. */
    public static void recordRetries(int retries) {
        current().ifPresent(builder -> builder.retries(retries));
    }
}
