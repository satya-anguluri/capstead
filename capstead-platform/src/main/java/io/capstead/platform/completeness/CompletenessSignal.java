package io.capstead.platform.completeness;

/**
 * Whether the evidence for one execution is complete enough to make absence meaningful.
 *
 * <p>COMPLETE means the recorded execution tree is provably the whole story, so a missing expected
 * tool is a real observation. SUSPECT means the platform cannot prove completeness — the rule must
 * return INSUFFICIENT_EVIDENCE rather than MATCHED.
 */
public enum CompletenessSignal {
    COMPLETE,
    SUSPECT
}