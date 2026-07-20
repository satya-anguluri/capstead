package io.capstead.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assembles {@link PipelineRun}s from already-recorded {@link CapabilityExecution}s — at read time,
 * with <strong>no interception, code or schema changes</strong> in the client application.
 *
 * <p>Why this works: within one pipeline run the steps execute sequentially, so their member
 * executions have non-overlapping time intervals and a strictly increasing step index. The assembler
 * scans candidate executions in start order and greedily extends the run whose last step finished
 * most recently (tightest fit), subject to three rules:
 *
 * <ol>
 *   <li><em>Forward progress</em> — an execution only joins a run when its step index is greater
 *       than the run's last step index (steps may be skipped, e.g. best-effort stages, but a run
 *       never revisits an earlier step; a repeat of the same or an earlier step starts a new run);</li>
 *   <li><em>No overlap</em> — the execution must start at or after the run's last step finished
 *       (with a small tolerance for timestamp rounding);</li>
 *   <li><em>Bounded gap</em> — the idle time between steps must not exceed the pipeline's
 *       {@code maxGap}.</li>
 * </ol>
 *
 * <p>Only <em>root</em> executions (no parent) participate: steps that are nested inside another
 * capability are already visible through the parent's drill-down and would double-count.
 *
 * <p><strong>Known limitation:</strong> two runs of the same pipeline executing concurrently and
 * interleaving in time cannot always be attributed perfectly, because the execution record carries
 * no thread or trace identity. The greedy tightest-fit rule keeps aggregate statistics (run count,
 * total cost) correct and mis-assigns wall time only in that overlap case.
 */
public final class PipelineAssembler {

    /** Tolerance for timestamp rounding between a step's recorded end and the next step's start. */
    private static final Duration OVERLAP_TOLERANCE = Duration.ofMillis(250);

    private PipelineAssembler() {
    }

    /**
     * Assembles runs of {@code definition} from candidate executions (any order, other capabilities
     * and nested executions tolerated and ignored). Returns runs sorted most-recent-first.
     */
    public static List<PipelineRun> assemble(PipelineDefinition definition,
                                             List<CapabilityExecution> executions) {
        List<CapabilityExecution> candidates = new ArrayList<>();
        for (CapabilityExecution execution : executions) {
            if (execution.parentExecutionId() == null
                    && execution.startedAt() != null
                    && definition.stepIndex(execution.capabilityName()) >= 0) {
                candidates.add(execution);
            }
        }
        candidates.sort(Comparator.comparing(CapabilityExecution::startedAt));

        List<OpenRun> open = new ArrayList<>();
        for (CapabilityExecution execution : candidates) {
            int index = definition.stepIndex(execution.capabilityName());
            OpenRun best = null;
            for (OpenRun run : open) {
                if (run.accepts(execution, index, definition.maxGap())
                        && (best == null || run.lastEnd.isAfter(best.lastEnd))) {
                    best = run;
                }
            }
            if (best != null) {
                best.add(execution, index);
            } else {
                open.add(new OpenRun(execution, index));
            }
        }

        List<PipelineRun> runs = new ArrayList<>(open.size());
        for (OpenRun run : open) {
            runs.add(new PipelineRun(definition.name(), run.members));
        }
        runs.sort(Comparator.comparing(PipelineRun::startedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return runs;
    }

    private static final class OpenRun {
        private final List<CapabilityExecution> members = new ArrayList<>();
        private int lastIndex;
        private Instant lastEnd;

        OpenRun(CapabilityExecution first, int index) {
            add(first, index);
        }

        boolean accepts(CapabilityExecution execution, int index, Duration maxGap) {
            if (index <= lastIndex || lastEnd == null) {
                return false;
            }
            Instant start = execution.startedAt();
            if (start.isBefore(lastEnd.minus(OVERLAP_TOLERANCE))) {
                return false;
            }
            return Duration.between(lastEnd, start).compareTo(maxGap) <= 0;
        }

        void add(CapabilityExecution execution, int index) {
            members.add(execution);
            lastIndex = index;
            lastEnd = PipelineRun.endOf(execution);
        }
    }
}
