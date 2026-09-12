package io.capstead.platform.completeness;

import io.capstead.core.CapabilityExecution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the completeness signal for a native execution tree from observable 0.9.0 properties.
 *
 * <p>0.9.0 records no first-class "execution tree closed" marker, so completeness is derived from
 * structural invariants of synchronous in-process recording. Each condition below is an observable
 * property, not an assumption:
 *
 * <ol>
 *   <li><b>Root execution:</b> {@code parentExecutionId == null}. A root execution is published in
 *       the interceptor's {@code finally} block, after every nested call has completed its own
 *       publication — children always complete before their parent in synchronous AOP recording.</li>
 *   <li><b>Self-consistent subtree:</b> the query returns the root first (parents before children
 *       is the documented {@code subtree()} contract) and every returned child's
 *       {@code parentExecutionId} resolves to another execution in the returned set. A child that
 *       points outside the set means a publication gap.</li>
 *   <li><b>Closed executions only:</b> every returned execution has non-null {@code finishedAt}
 *       and a non-negative {@code durationMs}.</li>
 *   <li><b>In-process recording:</b> the recorder mode is SYNC or BEST_EFFORT. Cross-instance or
 *       async deployments can publish children after the root arrives, so absence within the
 *       queried subtree could be a publication-lag artifact — such trees are SUSPECT by rule.</li>
 * </ol>
 *
 * <p>Stated limitation: 0.9.0 does not record a first-class "tree closed" marker. The conditions
 * above are the provable subset; if 0.9.0 later records a completeness marker, this policy should
 * adopt it as a deliberate change. Under any deployment outside the four conditions, Phase A
 * abstains (SUSPECT → INSUFFICIENT_EVIDENCE) rather than inventing evidence.
 */
public final class NativeCompletenessPolicy {

    /** Whether the recording delivery can prove in-process ordering. */
    public enum RecordingMode {
        /** SYNC or BEST_EFFORT in-process recording: children publish before parents. */
        IN_PROCESS,
        /** ASYNC or cross-instance recording: ordering cannot be proven. */
        NOT_PROVABLE
    }

    private final RecordingMode recordingMode;

    public NativeCompletenessPolicy(RecordingMode recordingMode) {
        this.recordingMode = recordingMode == null ? RecordingMode.IN_PROCESS : recordingMode;
    }

    /** Assesses completeness for one root execution and its queried subtree. */
    public CompletenessSignal assess(CapabilityExecution root, List<CapabilityExecution> subtree) {
        if (recordingMode == RecordingMode.NOT_PROVABLE) {
            return CompletenessSignal.SUSPECT;
        }
        if (root.parentExecutionId() != null) {
            return CompletenessSignal.SUSPECT;   // not a root — cannot anchor a tree
        }
        if (subtree.isEmpty() || !subtree.get(0).executionId().equals(root.executionId())) {
            return CompletenessSignal.SUSPECT;   // the query did not return the root first
        }
        var seen = new HashSet<String>();
        for (CapabilityExecution e : subtree) {
            if (e.finishedAt() == null || e.durationMs() < 0) {
                return CompletenessSignal.SUSPECT;   // unfinished execution in the recorded set
            }
            if (!e.executionId().equals(root.executionId())) {
                if (e.parentExecutionId() == null
                        || !seen.contains(e.parentExecutionId())) {
                    // A child whose parent is outside the recorded set: orphan/gap.
                    // Note: the root is added to `seen` after this check via the flow below.
                    if (!e.parentExecutionId().equals(root.executionId())
                            && !seen.contains(e.parentExecutionId())) {
                        return CompletenessSignal.SUSPECT;
                    }
                }
            }
            seen.add(e.executionId());
        }
        return CompletenessSignal.COMPLETE;
    }
}