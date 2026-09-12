package io.capstead.platform.ingest;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The platform's evaluation queue: root execution IDs only.
 *
 * <p>Deliberately stores NOTHING about the executions themselves — no records, no copies, no
 * snapshots. Capstead 0.9.0 owns execution state (its stores); the platform only needs to know
 * WHICH root executions to evaluate, and reads canonical data through
 * {@code CapabilityExecutionQuery.subtree()} at evaluation time.
 */
public final class EvaluationQueue {

    private final Queue<String> rootIds = new ConcurrentLinkedQueue<>();
    private final Set<String> enqueued = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** Queues a completed ROOT execution for evaluation. Child executions are ignored upstream. */
    public void submit(String rootExecutionId) {
        if (rootExecutionId == null || rootExecutionId.isBlank()) {
            return;
        }
        if (enqueued.add(rootExecutionId)) {
            rootIds.add(rootExecutionId);
        }
    }

    /** Pops the next root execution id to evaluate, or null when empty. */
    public String poll() {
        String id = rootIds.poll();
        if (id != null) {
            enqueued.remove(id);
        }
        return id;
    }

    /** How many root ids are pending evaluation. */
    public int pending() {
        return rootIds.size();
    }
}