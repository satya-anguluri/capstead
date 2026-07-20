package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;
import io.capstead.core.PipelineAssembler;
import io.capstead.core.PipelineDefinition;
import io.capstead.core.PipelineRun;
import io.capstead.core.PipelineScorecard;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-time pipeline assembly: sequential step executions group into runs with end-to-end wall
 * time, without any recording or schema change.
 */
class PipelineAssemblerTest {

    private static final Instant T0 = Instant.parse("2026-07-20T01:40:00Z");

    private final PipelineDefinition lesson = new PipelineDefinition(
            "Generate Lesson Pipeline", "EngineerPrep", "Content Team", "",
            List.of("Author Lesson", "Write Episode", "Synthesize Episode Audio",
                    "Generate Assessments", "Generate GitHub Repo"),
            Duration.ofMinutes(5));

    private static CapabilityExecution exec(String name, Instant start, Duration duration) {
        return exec(name, start, duration, true, null);
    }

    private static CapabilityExecution exec(String name, Instant start, Duration duration,
                                            boolean success, String parentId) {
        return CapabilityExecution.builder(name, "1")
                .executionId(name + "@" + start)
                .parentExecutionId(parentId)
                .startedAt(start)
                .finishedAt(start.plus(duration))
                .durationMs(duration.toMillis())
                .success(success)
                .build();
    }

    @Test
    void assemblesSequentialStepsIntoOneRunWithEndToEndWallTime() {
        // The real shape of a lesson run: 5 steps back-to-back over ~10 minutes.
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(7)), Duration.ofMinutes(2)),
                exec("Synthesize Episode Audio", T0.plus(Duration.ofMinutes(9)), Duration.ofMinutes(1)),
                exec("Generate Assessments", T0.plus(Duration.ofMinutes(10)), Duration.ofMinutes(1)),
                exec("Generate GitHub Repo", T0.plus(Duration.ofMinutes(11)), Duration.ofMinutes(1)));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(1);
        PipelineRun run = runs.get(0);
        assertThat(run.executions()).hasSize(5);
        assertThat(run.startedAt()).isEqualTo(T0);
        assertThat(run.wallTimeMs()).isEqualTo(Duration.ofMinutes(12).toMillis());
        assertThat(run.success()).isTrue();
    }

    @Test
    void stepIndexRegressionStartsANewRun() {
        // Two back-to-back runs: the second starts with the first step again.
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(6)), Duration.ofMinutes(2)),
                exec("Author Lesson", T0.plus(Duration.ofMinutes(9)), Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(15)), Duration.ofMinutes(2)));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(2);
        // Most-recent-first.
        assertThat(runs.get(0).startedAt()).isEqualTo(T0.plus(Duration.ofMinutes(9)));
        assertThat(runs.get(1).startedAt()).isEqualTo(T0);
        assertThat(runs.get(0).executions()).hasSize(2);
        assertThat(runs.get(1).executions()).hasSize(2);
    }

    @Test
    void gapBeyondMaxGapStartsANewRun() {
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                // 20 minutes idle > 5 minute max-gap: this is someone running just the episode step later.
                exec("Write Episode", T0.plus(Duration.ofMinutes(26)), Duration.ofMinutes(2)));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(2);
    }

    @Test
    void skippedBestEffortStepsStillAssembleOneRun() {
        // Audio + assessments skipped (best-effort failures logged, capability never invoked).
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(6)), Duration.ofMinutes(2)),
                exec("Generate GitHub Repo", T0.plus(Duration.ofMinutes(8)), Duration.ofMinutes(1)));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).executions()).hasSize(3);
    }

    @Test
    void nestedExecutionsAreExcluded() {
        // A Generate Scene-style child (recorded with a parent) must not join or duplicate runs.
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(2)), Duration.ofMinutes(1), true, "parent-123"));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).executions()).hasSize(1);
        assertThat(runs.get(0).executions().get(0).capabilityName()).isEqualTo("Author Lesson");
    }

    @Test
    void failedStepMarksTheRunFailed() {
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Write Episode", T0.plus(Duration.ofMinutes(6)), Duration.ofMinutes(1), false, null));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).success()).isFalse();
    }

    @Test
    void otherCapabilitiesAreIgnored() {
        List<CapabilityExecution> executions = List.of(
                exec("Author Lesson", T0, Duration.ofMinutes(6)),
                exec("Classify Question", T0.plus(Duration.ofMinutes(6)), Duration.ofSeconds(3)));

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, executions);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).executions()).hasSize(1);
    }

    @Test
    void scorecardAggregatesRunsWithCostAndModels() {
        CapabilityExecution author = CapabilityExecution.builder("Author Lesson", "1")
                .executionId("a1").startedAt(T0).finishedAt(T0.plus(Duration.ofMinutes(6)))
                .durationMs(Duration.ofMinutes(6).toMillis())
                .addModelInvocation(new ModelInvocation("claude-sonnet", 1000, 30000,
                        new BigDecimal("0.45"), T0))
                .build();
        CapabilityExecution episode = CapabilityExecution.builder("Write Episode", "1")
                .executionId("e1").startedAt(T0.plus(Duration.ofMinutes(6)))
                .finishedAt(T0.plus(Duration.ofMinutes(8)))
                .durationMs(Duration.ofMinutes(2).toMillis())
                .addModelInvocation(new ModelInvocation("nova-pro", 500, 4000,
                        new BigDecimal("0.05"), T0.plus(Duration.ofMinutes(6))))
                .build();

        List<PipelineRun> runs = PipelineAssembler.assemble(lesson, List.of(author, episode));
        PipelineScorecard scorecard = PipelineScorecard.of(lesson, runs);

        assertThat(scorecard.runs()).isEqualTo(1);
        assertThat(scorecard.successRate()).isEqualTo(1.0);
        assertThat(scorecard.avgWallTimeMs()).isEqualTo(Duration.ofMinutes(8).toMillis());
        assertThat(scorecard.totalCost()).isEqualByComparingTo("0.50");
        assertThat(scorecard.models()).containsExactly("claude-sonnet", "nova-pro");
        assertThat(scorecard.lastRunAt()).isEqualTo(T0);
    }
}
