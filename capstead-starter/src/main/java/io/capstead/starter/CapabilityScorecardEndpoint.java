package io.capstead.starter;

import io.capstead.core.CapabilityScorecard;
import io.capstead.runtime.CapabilityExecutionQuery;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.List;

/**
 * Actuator endpoint exposing per-capability scorecards and execution history.
 *
 * <ul>
 *   <li>{@code GET /actuator/capabilityscorecard} — scorecards for every capability version
 *       (invocations, success rate, latency, tokens, cost).</li>
 *   <li>{@code GET /actuator/capabilityscorecard/{name}} — recent execution history for one
 *       capability, like a Spring Batch job explorer.</li>
 * </ul>
 */
@Endpoint(id = "capabilityscorecard")
public class CapabilityScorecardEndpoint {

    private final CapabilityExecutionQuery store;

    public CapabilityScorecardEndpoint(CapabilityExecutionQuery store) {
        this.store = store;
    }

    @ReadOperation
    public List<CapabilityScorecard> scorecards() {
        return store.scorecards();
    }

    @ReadOperation
    public List<CapabilityExecutionView> history(@Selector String name) {
        return store.recentFor(name).stream().map(CapabilityExecutionView::of).toList();
    }
}
