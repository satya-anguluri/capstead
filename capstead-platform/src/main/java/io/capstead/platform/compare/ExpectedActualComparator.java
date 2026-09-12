package io.capstead.platform.compare;

import io.capstead.platform.expectation.ExecutionExpectation;

/**
 * Compares one applicable expectation against the observed shape of one native execution.
 *
 * <p>Phase A implements exactly two outcomes: EXPECTED_PRESENT (the expected tool identity appears
 * among the observed child tool calls) and EXPECTED_MISSING (it does not). Reserved outcomes are
 * intentionally unimplemented.
 *
 * <p>The comparator never persists anything and never applies rules — it produces the diff that
 * rules consume.
 */
public final class ExpectedActualComparator {

    private final NativeExecutionAdapter adapter;

    public ExpectedActualComparator(NativeExecutionAdapter adapter) {
        this.adapter = adapter;
    }

    /** Compares one applicable expectation against the observed execution. */
    public ExpectedActualDiff compare(ExecutionExpectation expectation,
                                      NativeExecutionAdapter.ObservedExecution observed) {
        boolean present = adapter.observed(expectation, observed);
        return new ExpectedActualDiff(
                expectation.expectationId(),
                expectation.revision(),
                expectation.capabilityName(),
                expectation.expectedOperation(),
                present ? ExpectedActualDiff.Outcome.EXPECTED_PRESENT
                        : ExpectedActualDiff.Outcome.EXPECTED_MISSING,
                observed.observedToolCalls(),
                observed.observedToolCalls().stream()
                        .filter(t -> !t.equals(expectation.expectedOperation()))
                        .toList(),
                observed);
    }
}