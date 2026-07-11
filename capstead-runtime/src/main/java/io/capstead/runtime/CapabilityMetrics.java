package io.capstead.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Records capability execution timings into a Micrometer {@link MeterRegistry}.
 *
 * <p>Emits one timer per {@code capability + version + domain + outcome} under the meter name
 * {@value #TIMER}. Capstead measures capabilities without owning their execution: the timing is
 * captured by an AOP interceptor wrapped around the {@code @Capability} method.
 */
public class CapabilityMetrics {

    /** Meter name for capability execution timers. */
    public static final String TIMER = "capstead.capability.execution";

    private final MeterRegistry meterRegistry;

    public CapabilityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a single execution.
     *
     * @param name    capability business name
     * @param version capability version
     * @param domain  owning domain (empty is normalized to {@code "none"})
     * @param outcome {@code "success"} or {@code "error"}
     * @param nanos   elapsed time in nanoseconds
     */
    public void record(String name, String version, String domain, String outcome, long nanos) {
        Timer.builder(TIMER)
                .tag("capability", name)
                .tag("version", version)
                .tag("domain", (domain == null || domain.isEmpty()) ? "none" : domain)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
