package io.capstead.core;

import java.math.BigDecimal;

/**
 * Aggregated, per-capability scorecard derived from a stream of {@link CapabilityExecution}s.
 *
 * <p>This is the enterprise-facing read model — the numbers on the dashboard and the
 * {@code /actuator/capabilityscorecard} endpoint: how often a capability ran, how reliable and how
 * fast it is, and what it consumed and cost. Unlike raw Micrometer stats, it includes token and
 * cost aggregates, which is what platform teams actually act on.
 */
public record CapabilityScorecard(
        String name,
        String version,
        long invocations,
        double successRate,
        double avgLatencyMs,
        double avgInputTokens,
        double avgOutputTokens,
        double avgCost,
        BigDecimal totalCost) {

    /** Stable identity across versions: {@code name@version}. */
    public String coordinates() {
        return name + "@" + version;
    }
}
