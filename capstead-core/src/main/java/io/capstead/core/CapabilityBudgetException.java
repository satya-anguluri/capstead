package io.capstead.core;

import java.math.BigDecimal;

/**
 * Thrown when a capability's daily budget has been reached and further invocations are blocked.
 *
 * <p>Carries the configured budget and the spend accumulated so far today, so callers and operators
 * can see exactly why the capability was gated.
 */
public class CapabilityBudgetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String capabilityName;
    private final BigDecimal budget;
    private final BigDecimal spentToday;

    public CapabilityBudgetException(String capabilityName, BigDecimal budget, BigDecimal spentToday) {
        super("Daily budget exceeded for capability '" + capabilityName + "': budget=" + budget
                + ", spent today=" + spentToday);
        this.capabilityName = capabilityName;
        this.budget = budget;
        this.spentToday = spentToday;
    }

    public String capabilityName() {
        return capabilityName;
    }

    public BigDecimal budget() {
        return budget;
    }

    public BigDecimal spentToday() {
        return spentToday;
    }
}
