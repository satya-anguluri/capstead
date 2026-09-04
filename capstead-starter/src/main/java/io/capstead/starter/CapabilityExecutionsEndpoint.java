package io.capstead.starter;

import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityExecutionQuery;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actuator endpoint exposing individual capability executions and their nested-call trees.
 *
 * <ul>
 *   <li>{@code GET /actuator/capabilityexecutions} — the recent execution history (most recent
 *       first), each carrying its id, parent id, timing, outcome and per-model invocations.</li>
 *   <li>{@code GET /actuator/capabilityexecutions/{id}} — one execution and everything beneath it,
 *       nested to full depth. Returns {@code null} when the id is unknown or has aged out of an
 *       in-memory history.</li>
 * </ul>
 *
 * <p><b>The {@code {id}} response used to be one level deep.</b> It served an execution plus its direct
 * children, under a type called {@code ExecutionTree}, so a composed capability that called a capability
 * that called another reported the middle layer and stopped. A caller wanting the real tree had to walk it
 * themselves, one request per level, and a caller who did not know that got a truncated answer that looked
 * complete. The nesting is now recursive, which changes the JSON shape of this endpoint: {@code children}
 * holds trees rather than executions.
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
        return nest(store.subtree(id));
    }

    /**
     * Build the nested shape from the flat, ordered subtree.
     *
     * <p>One pass, no sorting, because {@link CapabilityExecutionQuery#subtree} promises parents before
     * children: by the time a node is read its parent's node already exists to attach it to.
     *
     * <p>A node whose parent is not in the result is attached at the top rather than dropped. That should be
     * unreachable given the ordering contract, and dropping it would be the wrong failure anyway — losing a
     * branch silently is how a tree misleads, and an execution shown in the wrong place is at least visible.
     */
    private static ExecutionTree nest(List<CapabilityExecution> ordered) {
        if (ordered.isEmpty()) {
            return null;
        }
        Map<String, ExecutionTree> byId = new LinkedHashMap<>();
        ExecutionTree root = null;
        for (CapabilityExecution execution : ordered) {
            // FIRST OCCURRENCE WINS.
            //
            // An unconditional put would replace a node that already has children attached to it, orphaning
            // them — a corrupted shape rather than a duplicated one. The stores are not supposed to return
            // an id twice, but this reads whatever they hand it, and being resilient to a malformed tree is
            // cheaper here than trusting two implementations to stay correct forever. Raised in review.
            if (byId.containsKey(execution.executionId())) {
                continue;
            }
            ExecutionTree node = new ExecutionTree(CapabilityExecutionView.of(execution), new ArrayList<>());
            byId.put(execution.executionId(), node);
            if (root == null) {
                root = node;
                continue;
            }
            ExecutionTree parent = byId.get(execution.parentExecutionId());
            (parent == null ? root : parent).children().add(node);
        }
        return root;
    }

    /**
     * One execution together with the tree of nested capability calls beneath it.
     *
     * @param children direct children, each carrying its own children — empty for a leaf, never null
     */
    public record ExecutionTree(CapabilityExecutionView execution, List<ExecutionTree> children) {
    }
}
