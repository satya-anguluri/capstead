package io.capstead.core;

/**
 * Aggregated runtime statistics for one capability version, derived from Micrometer timers.
 *
 * <p>This is the numbers layer behind the dashboard: how often a capability ran, how long it took
 * on average, and how often it succeeded. Like {@link CapabilityMetadata}, it is a pure read model
 * with no execution or provider detail.
 */
public record CapabilityStats(
        String name,
        String version,
        long invocations,
        double averageMillis,
        double successRate) {

    /** Stable identity across versions: {@code name@version}. */
    public String coordinates() {
        return name + "@" + version;
    }
}
