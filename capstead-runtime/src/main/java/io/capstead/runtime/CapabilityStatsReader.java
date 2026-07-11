package io.capstead.runtime;

import io.capstead.core.CapabilityStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Computes {@link CapabilityStats} for every registered capability by reading the Micrometer
 * timers emitted by {@link CapabilityMetrics}.
 *
 * <p>This is the read side of the metrics story — it turns raw timers into the invocation count,
 * average latency and success rate the dashboard and {@code /actuator/capabilitymetrics} endpoint
 * display.
 */
public class CapabilityStatsReader {

    private final CapabilityRegistry registry;
    private final MeterRegistry meterRegistry;

    public CapabilityStatsReader(CapabilityRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
    }

    /** Stats for all registered capabilities, in registration order. */
    public List<CapabilityStats> stats() {
        List<CapabilityStats> result = new ArrayList<>();
        for (CapabilityDescriptor descriptor : registry.all()) {
            result.add(statsFor(descriptor));
        }
        return result;
    }

    private CapabilityStats statsFor(CapabilityDescriptor descriptor) {
        String name = descriptor.name();
        String version = descriptor.metadata().version();

        Collection<Timer> timers = meterRegistry.find(CapabilityMetrics.TIMER)
                .tag("capability", name)
                .tag("version", version)
                .timers();

        long invocations = 0;
        double totalMillis = 0.0;
        long successes = 0;
        for (Timer timer : timers) {
            long count = timer.count();
            invocations += count;
            totalMillis += timer.totalTime(TimeUnit.MILLISECONDS);
            if ("success".equals(timer.getId().getTag("outcome"))) {
                successes += count;
            }
        }

        double average = invocations == 0 ? 0.0 : totalMillis / invocations;
        double successRate = invocations == 0 ? 1.0 : (double) successes / invocations;
        return new CapabilityStats(name, version, invocations, average, successRate);
    }
}
