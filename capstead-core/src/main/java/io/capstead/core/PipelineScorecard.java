package io.capstead.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated read model for one declared pipeline — the pipeline analogue of
 * {@link CapabilityScorecard}: how often the pipeline ran end-to-end, how reliable it is, and what a
 * whole run takes in wall-clock time and cost.
 *
 * <p>{@code avgWallTimeMs} is the number per-capability latency cannot express: the full first-step
 * start → last-step end span of a run, including inter-step time.
 */
public record PipelineScorecard(
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
        Instant lastRunAt) {

    /** Builds the scorecard for a definition from its assembled runs. */
    public static PipelineScorecard of(PipelineDefinition definition, List<PipelineRun> runs) {
        long count = runs.size();
        long succeeded = 0;
        double wallSum = 0;
        BigDecimal totalCost = null;
        Instant lastRunAt = null;
        java.util.List<String> models = new java.util.ArrayList<>();
        for (PipelineRun run : runs) {
            if (run.success()) {
                succeeded++;
            }
            wallSum += run.wallTimeMs();
            BigDecimal cost = run.totalCost();
            if (cost != null) {
                totalCost = totalCost == null ? cost : totalCost.add(cost);
            }
            Instant started = run.startedAt();
            if (started != null && (lastRunAt == null || started.isAfter(lastRunAt))) {
                lastRunAt = started;
            }
            for (String model : run.models()) {
                if (!models.contains(model)) {
                    models.add(model);
                }
            }
        }
        return new PipelineScorecard(
                definition.name(),
                definition.domain(),
                definition.owner(),
                definition.steps(),
                count,
                count == 0 ? 1.0 : (double) succeeded / count,
                count == 0 ? 0.0 : wallSum / count,
                count == 0 || totalCost == null ? 0.0 : totalCost.doubleValue() / count,
                totalCost == null ? BigDecimal.ZERO : totalCost,
                List.copyOf(models),
                lastRunAt);
    }
}
