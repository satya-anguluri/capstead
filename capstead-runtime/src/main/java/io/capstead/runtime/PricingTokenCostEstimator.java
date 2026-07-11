package io.capstead.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * A {@link TokenCostEstimator} driven by per-model token rates.
 *
 * <p>Rates are expressed as price per one million tokens (the common vendor unit). Cost is
 * {@code (inputTokens * inputRate + outputTokens * outputRate) / 1_000_000}, rounded to 6 decimal
 * places. Unknown models return {@code null}.
 */
public class PricingTokenCostEstimator implements TokenCostEstimator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** Price per one million input and output tokens for a model. */
    public record ModelRate(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }

    private final Map<String, ModelRate> rates;

    public PricingTokenCostEstimator(Map<String, ModelRate> rates) {
        this.rates = rates;
    }

    @Override
    public BigDecimal estimate(String model, int inputTokens, int outputTokens) {
        if (model == null) {
            return null;
        }
        ModelRate rate = rates.get(model);
        if (rate == null) {
            return null;
        }
        BigDecimal inputCost = rate.inputPerMillion().multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal outputCost = rate.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens));
        return inputCost.add(outputCost).divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
