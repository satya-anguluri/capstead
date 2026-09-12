package io.capstead.platform.rule;

import io.capstead.platform.compare.ExpectedActualDiff;

/**
 * The result of one rule evaluation: exactly three results exist.
 *
 * <ul>
 *   <li>{@code MATCHED} — the rule fired; an incident is produced.</li>
 *   <li>{@code NOT_MATCHED} — the evidence was complete and the expected behavior occurred; no
 *       incident.</li>
 *   <li>{@code INSUFFICIENT_EVIDENCE} — the platform cannot prove the tree is complete (SUSPECT);
 *       the rule abstains explicitly rather than deciding silently. Absence within incomplete
 *       evidence is not an incident.</li>
 * </ul>
 */
public record RuleOutcome(
        String ruleId,
        int ruleVersion,
        Result result,
        String incidentType,
        String explanation,
        ExpectedActualDiff diff) {

    public enum Result {
        MATCHED,
        NOT_MATCHED,
        INSUFFICIENT_EVIDENCE
    }
}