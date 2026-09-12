package io.capstead.platform.rule;

import io.capstead.platform.compare.ExpectedActualDiff;
import io.capstead.platform.completeness.CompletenessSignal;

/**
 * CAP-INC-031 v1 — the expected tool did not execute although the contract required it and the
 * evidence is complete.
 *
 * <p>Evaluation (frozen):
 *
 * <ul>
 *   <li>SUSPECT completeness → {@code INSUFFICIENT_EVIDENCE}: absence within unprovable evidence
 *       is not an incident. The rule abstains explicitly.</li>
 *   <li>EXPECTED_MISSING + COMPLETE → {@code MATCHED}: incident type
 *       {@code EXPECTED_TOOL_NOT_OBSERVED}.</li>
 *   <li>EXPECTED_PRESENT + COMPLETE → {@code NOT_MATCHED}: the contract was honored.</li>
 * </ul>
 *
 * <p>Unexpected operations present in the diff (e.g. a local fallback tool identity where the
 * contracted tool was expected) are recorded on the diff/evidence as observed facts. Phase A
 * claims only what the observable operation identity proves — no provider semantics are asserted
 * by this rule.
 */
public final class ExpectedToolNotObservedRule implements IncidentRule {

    public static final String INCIDENT_TYPE = "EXPECTED_TOOL_NOT_OBSERVED";

    @Override
    public String id() {
        return "CAP-INC-031";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public RuleOutcome evaluate(ExpectedActualDiff diff, CompletenessSignal completeness) {
        if (completeness == CompletenessSignal.SUSPECT) {
            return new RuleOutcome(id(), version(), RuleOutcome.Result.INSUFFICIENT_EVIDENCE,
                    null,
                    "Execution tree completeness is SUSPECT: absence of '" + diff.expectedOperation()
                            + "' cannot be distinguished from a publication gap.",
                    diff);
        }
        if (diff.outcome() == ExpectedActualDiff.Outcome.EXPECTED_MISSING) {
            return new RuleOutcome(id(), version(), RuleOutcome.Result.MATCHED,
                    INCIDENT_TYPE,
                    "Expected tool '" + diff.expectedOperation() + "' was not observed in a "
                            + "COMPLETE execution of '" + diff.capabilityName() + "'.",
                    diff);
        }
        return new RuleOutcome(id(), version(), RuleOutcome.Result.NOT_MATCHED,
                null,
                "Expected tool '" + diff.expectedOperation() + "' was observed; the contract was honored.",
                diff);
    }
}