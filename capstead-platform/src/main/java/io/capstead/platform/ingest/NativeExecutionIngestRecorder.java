package io.capstead.platform.ingest;

import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityExecutionRecorder;

import java.util.Objects;

/**
 * The platform's execution ingestion trigger — implements 0.9.0's {@code
 * CapabilityExecutionRecorder} and does NOTHING except queue root execution ids.
 *
 * <p>Deliberate behavior (Blocker 3 of the design review):
 *
 * <ul>
 *   <li>child executions (parentExecutionId != null) are ignored — the root closes last and
 *       carries the whole tree;</li>
 *   <li>root executions enqueue their executionId — the ONLY thing the platform retains;</li>
 *   <li>no execution records, copies, or snapshots are kept here. Canonical data is read through
 *       {@code CapabilityExecutionQuery.subtree(rootId)} at evaluation time.</li>
 * </ul>
 *
 * <p>This honors 0.9.0's ownership of execution state: the platform is an evaluation trigger, not
 * a second store.
 */
public final class NativeExecutionIngestRecorder implements CapabilityExecutionRecorder {

    private final EvaluationQueue queue;

    public NativeExecutionIngestRecorder(EvaluationQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    @Override
    public void record(CapabilityExecution execution) {
        if (execution == null) {
            return;
        }
        if (execution.parentExecutionId() != null) {
            return; // child execution: the root will trigger evaluation of the whole tree
        }
        queue.submit(execution.executionId());
    }
}