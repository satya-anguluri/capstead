package io.capstead.platform.expectation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the validated, versioned expectation set and answers the applicability question BEFORE
 * any comparison or rule evaluation.
 *
 * <p>Applicability ordering is a design rule, not an optimization: when a declared expectation's
 * condition is not met by an execution, there is no applicable contract — the pipeline produces no
 * diff and no rule evaluation. A legitimate agent choice (condition unmet) must never reach
 * {@code ExpectedToolNotObservedRule} as a NOT_MATCHED; absence of applicability is its own
 * outcome.
 *
 * <p>Revisions are monotonic: re-registration bumps the revision. Changes never rewrite
 * previously emitted expectations (Phase A keeps an append-only list with an active flag).
 */
public final class ExpectationRegistry {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong revisionCounter = new AtomicLong();

    private record Entry(long revision, ExecutionExpectation expectation) {}

    /** Registers a validated expectation at the next revision. */
    public ExecutionExpectation register(ExecutionExpectation expectation) {
        long revision = revisionCounter.incrementAndGet();
        ExecutionExpectation stamped = new ExecutionExpectation(
                expectation.expectationId(),
                expectation.capabilityName(),
                expectation.version(),
                expectation.expectedOperation(),
                expectation.expectedType(),
                expectation.required(),
                expectation.condition(),
                revision,
                expectation.active(),
                expectation.createdAt());
        entries.add(new Entry(revision, stamped));
        return stamped;
    }

    /** All active expectations for the given parent capability (any version). */
    public List<ExecutionExpectation> forCapability(String capabilityName) {
        return entries.stream()
                .map(Entry::expectation)
                .filter(e -> e.active() && e.capabilityName().equals(capabilityName))
                .toList();
    }

    /**
     * The expectations that APPLY to this execution: same capability, condition satisfied by the
     * execution's governed attributes. Empty means "no applicable expectation" — nothing to
     * compare and nothing for a rule to evaluate.
     */
    public List<ExecutionExpectation> applicableFor(String capabilityName,
                                                    String version,
                                                    Map<String, String> attributes) {
        return entries.stream()
                .map(Entry::expectation)
                .filter(ExecutionExpectation::active)
                .filter(e -> e.capabilityName().equals(capabilityName))
                .filter(e -> e.version() == null || e.version().equals(version))
                .filter(e -> e.isApplicableTo(attributes == null ? Map.of() : attributes))
                .toList();
    }

    /** Current registry revision (monotonic). */
    public long currentRevision() {
        return revisionCounter.get();
    }
}