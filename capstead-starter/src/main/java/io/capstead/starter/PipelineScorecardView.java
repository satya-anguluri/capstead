package io.capstead.starter;

import io.capstead.core.PipelineRun;
import io.capstead.core.PipelineScorecard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Serialization-friendly views of {@link PipelineScorecard} and {@link PipelineRun} for the
 * {@code capabilitypipelines} actuator endpoint (same rationale as {@link CapabilityExecutionView}:
 * plain record components serialize reliably; timestamps as ISO-8601 strings).
 */
public record PipelineScorecardView(
        String name,
        String domain,
        String owner,
        List<String> steps,
        long runs,
        double successRate,
        double avgWallTimeMs,
        double avgCost,
        BigDecimal totalCost,
        List<String> models,
        String lastRunAt) {

    static PipelineScorecardView of(PipelineScorecard scorecard) {
        return new PipelineScorecardView(
                scorecard.name(),
                scorecard.domain(),
                scorecard.owner(),
                scorecard.steps(),
                scorecard.runs(),
                scorecard.successRate(),
                scorecard.avgWallTimeMs(),
                scorecard.avgCost(),
                scorecard.totalCost(),
                scorecard.models(),
                text(scorecard.lastRunAt()));
    }

    /** One assembled run with its member step executions. */
    public record PipelineRunView(
            String runId,
            String pipelineName,
            String startedAt,
            String finishedAt,
            long wallTimeMs,
            boolean success,
            int inputTokens,
            int outputTokens,
            BigDecimal totalCost,
            List<String> models,
            List<CapabilityExecutionView> steps) {

        static PipelineRunView of(PipelineRun run) {
            return new PipelineRunView(
                    run.runId(),
                    run.pipelineName(),
                    text(run.startedAt()),
                    text(run.finishedAt()),
                    run.wallTimeMs(),
                    run.success(),
                    run.inputTokens(),
                    run.outputTokens(),
                    run.totalCost(),
                    run.models(),
                    run.executions().stream().map(CapabilityExecutionView::of).toList());
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
