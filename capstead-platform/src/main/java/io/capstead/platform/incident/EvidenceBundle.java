package io.capstead.platform.incident;

import io.capstead.platform.completeness.CompletenessSignal;

import java.util.List;

/**
 * The frozen, minimal proof of one diagnosis. Freezes exactly the deterministic facts that caused
 * the rule to fire and LINKS the native execution by id for deeper inspection — the execution
 * itself remains owned by Capstead 0.9.0 (its store, its retention); the bundle never copies it.
 *
 * <p>The bundle stays explainable even if execution retention later removes the original tree:
 * the facts that caused the firing are frozen here.
 *
 * @param nativeExecutionId      LINK to the deeper evidence (readable via the existing
 *                               Capstead execution endpoint)
 * @param expectationId          the contract that was violated
 * @param expectationRevision    the registry revision active at evaluation time
 * @param expectedOperation      the operation the contract required
 * @param observedOperations     the operation identities actually executed
 * @param unexpectedOperations   executed operations not contracted by this expectation
 *                               (Phase A: recorded as observed facts only — no provider semantics)
 * @param completeness           COMPLETE when the rule fired (MATCHED requires COMPLETE)
 * @param comparisonOutcome      the comparator outcome (Phase A: EXPECTED_MISSING for incidents)
 * @param ruleId                 e.g. CAP-INC-031
 * @param ruleVersion            e.g. 1
 */
public record EvidenceBundle(
        String nativeExecutionId,
        String expectationId,
        long expectationRevision,
        String expectedOperation,
        List<String> observedOperations,
        List<String> unexpectedOperations,
        CompletenessSignal completeness,
        String comparisonOutcome,
        String ruleId,
        int ruleVersion) {

    public EvidenceBundle {
        if (nativeExecutionId == null || nativeExecutionId.isBlank())
            throw new IllegalArgumentException("nativeExecutionId must not be blank");
        observedOperations = observedOperations == null ? List.of() : List.copyOf(observedOperations);
        unexpectedOperations = unexpectedOperations == null ? List.of() : List.copyOf(unexpectedOperations);
    }
}