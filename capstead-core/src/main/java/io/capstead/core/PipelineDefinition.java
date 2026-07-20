package io.capstead.core;

import java.time.Duration;
import java.util.List;

/**
 * A declared business pipeline: an ordered sequence of capabilities that together deliver one
 * user-facing outcome (e.g. Author Lesson → Write Episode → Synthesize Audio → Generate
 * Assessments).
 *
 * <p>Pipelines are a <em>read-model</em> concept: they require no interception, no code changes and
 * no schema changes in the client application. Runs are assembled at query time from the capability
 * executions the store has already recorded (see {@link PipelineAssembler}), so declaring a pipeline
 * retroactively surfaces historical runs too.
 *
 * @param name        unique pipeline name shown on the dashboard
 * @param domain      business domain (same grouping semantics as capability metadata)
 * @param owner       owning team
 * @param description optional human description
 * @param steps       ordered capability names that make up the pipeline; a run may skip steps
 *                    (best-effort stages) but never revisits an earlier one
 * @param maxGap      maximum idle time between one step finishing and the next starting for both to
 *                    be considered part of the same run
 */
public record PipelineDefinition(
        String name,
        String domain,
        String owner,
        String description,
        List<String> steps,
        Duration maxGap) {

    public static final Duration DEFAULT_MAX_GAP = Duration.ofMinutes(10);

    public PipelineDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capstead.pipelines: 'name' is required");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "capstead.pipelines['" + name + "']: at least one step is required");
        }
        domain = domain == null ? "" : domain;
        owner = owner == null ? "" : owner;
        description = description == null ? "" : description;
        steps = List.copyOf(steps);
        maxGap = maxGap == null ? DEFAULT_MAX_GAP : maxGap;
    }

    /** The position of the given capability in this pipeline, or {@code -1} when not a step. */
    public int stepIndex(String capabilityName) {
        return steps.indexOf(capabilityName);
    }
}
