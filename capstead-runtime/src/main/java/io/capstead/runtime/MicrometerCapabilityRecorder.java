package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

import java.util.concurrent.TimeUnit;

/**
 * A {@link CapabilityExecutionRecorder} that projects executions onto Micrometer.
 *
 * <p>Bridges Capstead's first-class executions to standard observability tooling (Prometheus,
 * Grafana, etc.) by emitting the execution timer via {@link CapabilityMetrics}. Registered only
 * when a {@code MeterRegistry} is present, so Micrometer stays optional.
 */
public class MicrometerCapabilityRecorder implements CapabilityExecutionRecorder {

    private final CapabilityMetrics metrics;

    public MicrometerCapabilityRecorder(CapabilityMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void record(CapabilityExecution execution) {
        long nanos = TimeUnit.MILLISECONDS.toNanos(execution.durationMs());
        String outcome = execution.success() ? "success" : "error";
        metrics.record(execution.capabilityName(), execution.version(), execution.domain(), outcome, nanos);
    }
}
