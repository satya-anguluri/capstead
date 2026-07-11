package io.capstead.runtime;

import java.math.BigDecimal;

/**
 * Estimates the monetary cost of a capability execution from its model and token usage.
 *
 * <p>Capstead does not know pricing intrinsically — it is configured per model. Implementations
 * return {@code null} when they cannot price a given model, in which case the execution simply
 * carries no cost.
 */
public interface TokenCostEstimator {

    /**
     * @param model        the model used (may be {@code null})
     * @param inputTokens  prompt tokens
     * @param outputTokens completion tokens
     * @return the estimated cost, or {@code null} if the model is unknown/unpriced
     */
    BigDecimal estimate(String model, int inputTokens, int outputTokens);
}
