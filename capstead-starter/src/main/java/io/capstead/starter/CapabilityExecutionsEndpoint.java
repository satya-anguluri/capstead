package io.capstead.starter;

import io.capstead.runtime.CapabilityExecutionQuery;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.List;

/**
 * Actuator endpoint exposing individual capability executions and their nested-call trees.
 *
 * <ul>
 *   <li>{@code GET /actuator/capabilityexecutions} — the recent execution history (most recent
 *       first), each carrying its id, parent id, timing, outcome and per-model invocations.</li>
 *   <li>{@code GET /actuator/capabilityexecutions/{id}} — one execution plus its direct children,
 *       so a composed capability's tree can be walked. Returns {@code null} when the id has aged out
 *       of history.</li>
 * </ul>
 */
@Endpoint(id = "capabilityexecutions")
public class CapabilityExecutionsEndpoint {

    private final CapabilityExecutionQuery store;

    public CapabilityExecutionsEndpoint(CapabilityExecutionQuery store) {
        this.store = store;
    }

    @ReadOperation
    public List<CapabilityExecutionView> executions() {
        return store.recent().stream().map(CapabilityExecutionView::of).toList();
    }

    @ReadOperation
    public ExecutionTree execution(@Selector String id) {
        return store.byId(id)
                .map(execution -> new ExecutionTree(
                        CapabilityExecutionView.of(execution),
                        store.childrenOf(id).stream().map(CapabilityExecutionView::of).toList()))
                .orElse(null);
    }

    /** One execution together with its direct nested-capability calls. */
    public record ExecutionTree(CapabilityExecutionView execution, List<CapabilityExecutionView> children) {
    }
}
