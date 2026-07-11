package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks accumulated capability spend per UTC day.
 *
 * <p>Registered as a {@link CapabilityExecutionRecorder}, it sums each execution's estimated cost
 * into a per-capability, per-day tally. The {@link CapabilityMethodInterceptor} reads
 * {@link #spentToday(String)} to enforce {@code @DailyBudget}. Executions without a cost estimate do
 * not count toward spend.
 */
public class CapabilityBudgetLedger implements CapabilityExecutionRecorder {

    private final Map<String, Map<LocalDate, BigDecimal>> spendByCapabilityAndDay = new HashMap<>();

    @Override
    public synchronized void record(CapabilityExecution execution) {
        BigDecimal cost = execution.estimatedCost();
        if (cost == null) {
            return;
        }
        spendByCapabilityAndDay
                .computeIfAbsent(execution.coordinates(), key -> new HashMap<>())
                .merge(today(), cost, BigDecimal::add);
    }

    /** Total estimated spend for the given {@code name@version} coordinates today (UTC). */
    public synchronized BigDecimal spentToday(String coordinates) {
        Map<LocalDate, BigDecimal> byDay = spendByCapabilityAndDay.get(coordinates);
        if (byDay == null) {
            return BigDecimal.ZERO;
        }
        return byDay.getOrDefault(today(), BigDecimal.ZERO);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
