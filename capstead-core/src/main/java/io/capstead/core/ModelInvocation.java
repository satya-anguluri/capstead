package io.capstead.core;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One model/LLM call made during a {@link CapabilityExecution}.
 *
 * <p>A single capability may call the model several times (retries, multi-step, fan-out); each call
 * is captured as a {@code ModelInvocation} so token usage and cost can be attributed <em>per model</em>,
 * not just per capability. Capstead does not execute the model — these are supplied via
 * {@code CapabilityExecutionContext.recordModelInvocation(...)} by whoever makes the call.
 *
 * @param model         the model identifier (e.g. {@code "claude-sonnet-4-6"}); may be {@code null} if unknown
 * @param inputTokens   prompt/input tokens for this call
 * @param outputTokens  completion/output tokens for this call
 * @param estimatedCost estimated cost of this call, or {@code null} if not priced
 * @param invokedAt     when the call was recorded, or {@code null}
 */
public record ModelInvocation(String model, int inputTokens, int outputTokens,
                              BigDecimal estimatedCost, Instant invokedAt) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
