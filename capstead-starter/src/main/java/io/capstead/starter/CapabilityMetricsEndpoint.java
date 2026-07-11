package io.capstead.starter;

import io.capstead.core.CapabilityStats;
import io.capstead.runtime.CapabilityStatsReader;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.List;

/**
 * Actuator endpoint exposing capability runtime metrics at
 * {@code GET /actuator/capabilitymetrics}.
 *
 * <p>Returns per-capability invocation counts, average latency and success rate — the numbers the
 * Capstead dashboard renders. Available only when a Micrometer {@code MeterRegistry} is present.
 */
@Endpoint(id = "capabilitymetrics")
public class CapabilityMetricsEndpoint {

    private final CapabilityStatsReader statsReader;

    public CapabilityMetricsEndpoint(CapabilityStatsReader statsReader) {
        this.statsReader = statsReader;
    }

    @ReadOperation
    public List<CapabilityStats> metrics() {
        return statsReader.stats();
    }
}
