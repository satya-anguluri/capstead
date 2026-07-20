package io.capstead.starter;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.PipelineAssembler;
import io.capstead.core.PipelineDefinition;
import io.capstead.core.PipelineRun;
import io.capstead.core.PipelineScorecard;
import io.capstead.runtime.CapabilityExecutionQuery;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.ArrayList;
import java.util.List;

/**
 * Actuator endpoint exposing declared pipelines ({@code capstead.pipelines}) with runs assembled at
 * read time from the execution store — the end-to-end wall-clock view of multi-capability flows.
 *
 * <ul>
 *   <li>{@code GET /actuator/capabilitypipelines} — a scorecard per declared pipeline
 *       (runs, success rate, average wall time, cost).</li>
 *   <li>{@code GET /actuator/capabilitypipelines/{name}} — recent runs of one pipeline, each with
 *       its member step executions.</li>
 * </ul>
 */
@Endpoint(id = "capabilitypipelines")
public class CapabilityPipelinesEndpoint {

    private final CapabilityExecutionQuery store;
    private final List<PipelineDefinition> definitions;

    public CapabilityPipelinesEndpoint(CapabilityExecutionQuery store, List<PipelineDefinition> definitions) {
        this.store = store;
        this.definitions = definitions;
    }

    @ReadOperation
    public List<PipelineScorecardView> scorecards() {
        List<PipelineScorecardView> out = new ArrayList<>(definitions.size());
        for (PipelineDefinition definition : definitions) {
            out.add(PipelineScorecardView.of(PipelineScorecard.of(definition, assemble(definition))));
        }
        return out;
    }

    @ReadOperation
    public List<PipelineScorecardView.PipelineRunView> runs(@Selector String name) {
        for (PipelineDefinition definition : definitions) {
            if (definition.name().equals(name)) {
                return assemble(definition).stream().map(PipelineScorecardView.PipelineRunView::of).toList();
            }
        }
        return List.of();
    }

    private List<PipelineRun> assemble(PipelineDefinition definition) {
        List<CapabilityExecution> candidates = new ArrayList<>();
        for (String step : definition.steps()) {
            candidates.addAll(store.recentFor(step));
        }
        return PipelineAssembler.assemble(definition, candidates);
    }
}
