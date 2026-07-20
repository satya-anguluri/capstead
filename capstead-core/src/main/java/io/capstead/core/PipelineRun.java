package io.capstead.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One assembled run of a {@link PipelineDefinition}: the ordered step executions that belong to a
 * single end-to-end pass, with wall-clock timing spanning the whole run — including the time spent
 * <em>between</em> steps, which per-capability latency can never show.
 *
 * <p>Runs are synthesized at read time by {@link PipelineAssembler}; nothing extra is recorded.
 *
 * @param pipelineName the pipeline this run belongs to
 * @param executions   the member step executions in start order (never empty)
 */
public record PipelineRun(String pipelineName, List<CapabilityExecution> executions) {

    public PipelineRun {
        if (executions == null || executions.isEmpty()) {
            throw new IllegalArgumentException("a pipeline run requires at least one execution");
        }
        executions = List.copyOf(executions);
    }

    /** Stable id for the run: the execution id of its first step. */
    public String runId() {
        return executions.get(0).executionId();
    }

    public Instant startedAt() {
        return executions.get(0).startedAt();
    }

    /** When the last step finished (falls back to start + duration when finishedAt is absent). */
    public Instant finishedAt() {
        return endOf(executions.get(executions.size() - 1));
    }

    /** Wall-clock duration of the whole run, first step start to last step end. */
    public long wallTimeMs() {
        Instant start = startedAt();
        Instant end = finishedAt();
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, end.toEpochMilli() - start.toEpochMilli());
    }

    /** {@code true} only when every member step succeeded. */
    public boolean success() {
        for (CapabilityExecution execution : executions) {
            if (!execution.success()) {
                return false;
            }
        }
        return true;
    }

    /** Total input tokens across all steps. */
    public int inputTokens() {
        int sum = 0;
        for (CapabilityExecution execution : executions) {
            sum += execution.inputTokens();
        }
        return sum;
    }

    /** Total output tokens across all steps. */
    public int outputTokens() {
        int sum = 0;
        for (CapabilityExecution execution : executions) {
            sum += execution.outputTokens();
        }
        return sum;
    }

    /** Total estimated cost across all steps, or {@code null} when no step is priced. */
    public BigDecimal totalCost() {
        BigDecimal total = null;
        for (CapabilityExecution execution : executions) {
            BigDecimal cost = execution.estimatedCost();
            if (cost != null) {
                total = total == null ? cost : total.add(cost);
            }
        }
        return total;
    }

    /** Distinct models used across all steps, in first-seen order. */
    public List<String> models() {
        List<String> out = new ArrayList<>();
        for (CapabilityExecution execution : executions) {
            for (ModelInvocation invocation : execution.modelInvocations()) {
                if (invocation.model() != null && !out.contains(invocation.model())) {
                    out.add(invocation.model());
                }
            }
        }
        return out;
    }

    /** The effective end instant of an execution ({@code finishedAt}, else start + duration). */
    static Instant endOf(CapabilityExecution execution) {
        if (execution.finishedAt() != null) {
            return execution.finishedAt();
        }
        if (execution.startedAt() == null) {
            return null;
        }
        return execution.startedAt().plusMillis(execution.durationMs());
    }
}
