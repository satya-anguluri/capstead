package io.capstead.platform.compare;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;
import io.capstead.platform.expectation.ExecutionExpectation;
import io.capstead.runtime.CapabilityExecutionQuery;

import java.util.List;
import java.util.Map;

/**
 * Derives the observable shape of one native execution for comparison, from {@code
 * CapabilityExecutionQuery} data only.
 *
 * <p>The platform never stores execution copies: every evaluation reads canonical data through the
 * query at evaluation time. Tool calls made by a native capability appear as child capability
 * executions whose capability name maps to the contracted tool identity (via the catalog's
 * deterministic naming convention {@code sanitize(name) + "_v" + sanitize(version)}), so the
 * adapter derives the observed tool-call identity set from the subtree — the native equivalent of
 * tool-call spans, with nothing re-recorded.
 */
public final class NativeExecutionAdapter {

    private final CapabilityExecutionQuery query;
    private final ToolNameLookup toolNames;

    public NativeExecutionAdapter(CapabilityExecutionQuery query, ToolNameLookup toolNames) {
        this.query = query;
        this.toolNames = toolNames;
    }

    /** Maps a capability coordinate to its contracted tool identity, if one is declared. */
    public interface ToolNameLookup {
        /** Returns the canonical expected-operation identity for this capability, or null. */
        String toolNameFor(String capabilityName, String version);
    }

    /** The observable shape of one execution. */
    public record ObservedExecution(
            String executionId,
            String parentExecutionId,
            String capabilityName,
            String version,
            boolean success,
            String errorType,
            List<String> observedToolCalls,      // contracted tool identities executed by children
            List<String> otherChildOperations,   // child capabilities that are not contracted tools
            List<ModelInvocation> modelInvocations,
            Map<String, String> attributes) {}

    /** Derives the observed shape for {@code root} and its whole subtree. */
    public ObservedExecution observe(CapabilityExecution root) {
        List<CapabilityExecution> subtree = query.subtree(root.executionId());
        List<String> toolCalls = subtree.stream()
                .skip(1) // the root itself is not one of its own child operations
                .map(e -> toolNames.toolNameFor(e.capabilityName(), e.version()))
                .filter(t -> t != null)
                .toList();
        List<String> otherChildren = subtree.stream()
                .skip(1)
                .map(e -> e.capabilityName() + "@" + e.version())
                .filter(name -> toolCalls.stream().noneMatch(name::equals))
                .toList();
        return new ObservedExecution(
                root.executionId(),
                root.parentExecutionId(),
                root.capabilityName(),
                root.version(),
                root.success(),
                root.errorType(),
                List.copyOf(toolCalls),
                List.copyOf(otherChildren),
                List.copyOf(root.modelInvocations()),
                root.attributes());
    }

    /** Convenience: expectation satisfied when its expected operation is among observed calls. */
    public boolean observed(ExecutionExpectation expectation, ObservedExecution observed) {
        return observed.observedToolCalls().stream()
                .anyMatch(t -> t.equals(expectation.expectedOperation()));
    }
}