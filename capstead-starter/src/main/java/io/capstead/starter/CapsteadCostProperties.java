package io.capstead.starter;

import io.capstead.runtime.PricingTokenCostEstimator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures per-model token pricing used to estimate capability cost.
 *
 * <p>Example:
 * <pre>
 * capstead.cost.models.claude-sonnet.input-per-million-tokens: 3.00
 * capstead.cost.models.claude-sonnet.output-per-million-tokens: 15.00
 * </pre>
 */
@ConfigurationProperties("capstead.cost")
public class CapsteadCostProperties {

    private Map<String, ModelPricing> models = new LinkedHashMap<>();

    public Map<String, ModelPricing> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelPricing> models) {
        this.models = models;
    }

    /** Converts the bound properties into estimator rates, skipping incompletely-priced models. */
    public Map<String, PricingTokenCostEstimator.ModelRate> toRates() {
        Map<String, PricingTokenCostEstimator.ModelRate> rates = new LinkedHashMap<>();
        models.forEach((model, pricing) -> {
            if (pricing.getInputPerMillionTokens() != null && pricing.getOutputPerMillionTokens() != null) {
                rates.put(model, new PricingTokenCostEstimator.ModelRate(
                        pricing.getInputPerMillionTokens(), pricing.getOutputPerMillionTokens()));
            }
        });
        return rates;
    }

    /** Input/output price per one million tokens for a single model. */
    public static class ModelPricing {
        private BigDecimal inputPerMillionTokens;
        private BigDecimal outputPerMillionTokens;

        public BigDecimal getInputPerMillionTokens() {
            return inputPerMillionTokens;
        }

        public void setInputPerMillionTokens(BigDecimal inputPerMillionTokens) {
            this.inputPerMillionTokens = inputPerMillionTokens;
        }

        public BigDecimal getOutputPerMillionTokens() {
            return outputPerMillionTokens;
        }

        public void setOutputPerMillionTokens(BigDecimal outputPerMillionTokens) {
            this.outputPerMillionTokens = outputPerMillionTokens;
        }
    }
}
