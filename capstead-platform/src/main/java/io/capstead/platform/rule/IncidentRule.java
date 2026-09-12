package io.capstead.platform.rule;

import io.capstead.platform.compare.ExpectedActualDiff;
import io.capstead.platform.completeness.CompletenessSignal;

/**
 * A deterministic incident rule: consumes a comparison diff plus the completeness signal and
 * returns exactly one of three results.
 *
 * <p>Rules never read execution content, never mutate anything, and always stamp their id and
 * version into the outcome. Exactly three results exist — there is no fourth "silent" outcome:
 * when evidence is insufficient the rule says so explicitly.
 */
public interface IncidentRule {

    String id();

    int version();

    RuleOutcome evaluate(ExpectedActualDiff diff, CompletenessSignal completeness);
}