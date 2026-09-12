package io.capstead.platform;

import io.capstead.platform.ingest.EvaluationQueue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates queued root executions: reads the canonical tree through {@code
 * CapabilityExecutionQuery.subtree()}, filters applicable expectations, compares, runs rules, and
 * saves incidents.
 *
 * <p>Ordering is the design rule: applicability BEFORE comparison, comparison BEFORE rules. A
 * legitimate agent choice (no applicable expectation) produces no diff and no rule evaluation.
 */
public final class IncidentEvaluator {

    private final io.capstead.runtime.CapabilityExecutionQuery query;
    private final io.capstead.platform.compare.NativeExecutionAdapter adapter;
    private final io.capstead.platform.expectation.ExpectationRegistry expectations;
    private final io.capstead.platform.compare.ExpectedActualComparator comparator;
    private final io.capstead.platform.completeness.NativeCompletenessPolicy completenessPolicy;
    private final List<io.capstead.platform.rule.IncidentRule> rules;
    private final io.capstead.platform.incident.IncidentStore store;

    public IncidentEvaluator(io.capstead.runtime.CapabilityExecutionQuery query,
                             io.capstead.platform.compare.NativeExecutionAdapter adapter,
                             io.capstead.platform.expectation.ExpectationRegistry expectations,
                             io.capstead.platform.compare.ExpectedActualComparator comparator,
                             io.capstead.platform.completeness.NativeCompletenessPolicy completenessPolicy,
                             List<io.capstead.platform.rule.IncidentRule> rules,
                             io.capstead.platform.incident.IncidentStore store) {
        this.query = query;
        this.adapter = adapter;
        this.expectations = expectations;
        this.comparator = comparator;
        this.completenessPolicy = completenessPolicy;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.store = store;
    }

    /** Evaluates every queued root execution. Returns the number of incidents produced. */
    public int evaluateAll(io.capstead.platform.ingest.EvaluationQueue queue) {
        int produced = 0;
        String rootId;
        while ((rootId = queue.poll()) != null) {
            produced += evaluateRoot(rootId);
        }
        return produced;
    }

    /** Evaluates one root execution. Returns the number of incidents produced (0 or more). */
    public int evaluateRoot(String rootExecutionId) {
        var rootOpt = query.byId(rootExecutionId);
        if (rootOpt.isEmpty()) {
            return 0;   // execution no longer present (retention) — nothing to evaluate
        }
        var root = rootOpt.get();
        var observed = adapter.observe(root);
        var subtree = query.subtree(root.executionId());
        var completeness = completenessPolicy.assess(root, subtree);

        var applicable = expectations.applicableFor(
                observed.capabilityName(), observed.version(), observed.attributes());
        if (applicable.isEmpty()) {
            return 0;   // no applicable expectation — no comparison, no rule evaluation
        }

        int produced = 0;
        for (var expectation : applicable) {
            if (expectation.required() == false) {
                continue;   // optional expectations are not violations when missing
            }
            var diff = comparator.compare(expectation, observed);
            for (var rule : rules) {
                var outcome = rule.evaluate(diff, completeness);
                if (outcome.result() == io.capstead.platform.rule.RuleOutcome.Result.MATCHED) {
                    store.save(new io.capstead.platform.incident.Incident(
                            "INC-" + UUID.randomUUID(),
                            outcome.incidentType(),
                            outcome.ruleId(),
                            outcome.ruleVersion(),
                            io.capstead.platform.incident.Severity.HIGH,
                            Instant.now(),
                            new io.capstead.platform.incident.EvidenceBundle(
                                    observed.executionId(),
                                    diff.expectationId(),
                                    diff.expectationRevision(),
                                    diff.expectedOperation(),
                                    observed.observedToolCalls(),
                                    diff.unexpectedOperations(),
                                    completeness == io.capstead.platform.completeness.CompletenessSignal.COMPLETE
                                            ? io.capstead.platform.completeness.CompletenessSignal.COMPLETE
                                            : io.capstead.platform.completeness.CompletenessSignal.SUSPECT,
                                    diff.outcome().name(),
                                    outcome.ruleId(),
                                    outcome.ruleVersion())));
                    produced++;
                }
            }
        }
        return produced;
    }
}