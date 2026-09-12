package io.capstead.platform.compare;

import java.util.List;

/**
 * The outcome of one expected-vs-actual comparison.
 *
 * <p>Phase A implements exactly two outcomes — {@code EXPECTED_PRESENT} and {@code EXPECTED_MISSING}.
 * The other comparison outcomes (order violations, wrong-parent placement, unexpected-only) are
 * reserved for later phases and are deliberately NOT implemented here; an unexpected child
 * operation present alongside a missing expected one is recorded on the diff as
 * {@code unexpectedOperations} evidence, but the comparison outcome stays EXPECTED_MISSING.
 */
public record ExpectedActualDiff(
        String expectationId,
        long expectationRevision,
        String capabilityName,
        String expectedOperation,
        Outcome outcome,
        List<String> observedToolCalls,
        List<String> unexpectedOperations,   // operations executed but not contracted by this expectation
        NativeExecutionAdapter.ObservedExecution observed) {

    public enum Outcome {
        /** The expected operation executed within the observed tree. */
        EXPECTED_PRESENT,
        /** The expected operation did not execute within the observed tree. */
        EXPECTED_MISSING
        // Reserved (NOT implemented in Phase A): ORDER_VIOLATED, WRONG_PARENT, UNEXPECTED_ONLY
    }
}