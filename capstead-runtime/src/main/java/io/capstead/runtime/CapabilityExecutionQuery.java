package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;

import java.util.List;
import java.util.Optional;

/**
 * Read side of the execution store — what the actuator endpoints and dashboard query.
 *
 * <p>The in-memory store implements this (per-instance, bounded), and so does the JDBC reader
 * (durable, cross-instance). When {@code capstead-jdbc} is enabled it supplies the primary
 * implementation, so the scorecard and execution-history endpoints serve durable data that survives
 * restarts and aggregates across instances.
 */
public interface CapabilityExecutionQuery {

    /** Scorecards for every capability version seen. */
    List<CapabilityScorecard> scorecards();

    /** Most-recent-first execution history. */
    List<CapabilityExecution> recent();

    /** Most-recent-first history for a single capability (all versions). */
    List<CapabilityExecution> recentFor(String name);

    /** The execution with the given id, if present. */
    Optional<CapabilityExecution> byId(String executionId);

    /** Most-recent-first direct children of the given execution (its nested capability calls). */
    List<CapabilityExecution> childrenOf(String executionId);

    /**
     * The given execution and every descendant beneath it, in one call.
     *
     * <p>{@link #childrenOf} answers one level. Reassembling a whole tree from it costs a round trip per
     * level and makes every caller write the same traversal — and a caller that stops early, or forgets that
     * a child can have children, silently reports a smaller tree rather than failing.
     *
     * <p><b>Ordering is part of the contract.</b> The root comes first, and every node appears after its own
     * parent, so a consumer can build the tree in a single pass without sorting or a second index. Siblings
     * keep the order the underlying store gives them.
     *
     * <p>Returns an empty list when the id is unknown — a subtree of nothing, not a subtree containing a
     * fabricated root. A known execution with no children returns exactly itself.
     *
     * <p>This deliberately returns the same {@link CapabilityExecution} records the rest of this interface
     * returns, rather than a new export type. An export shape that consumers persist and diff is a contract
     * worth settling once, alongside the recorded events it will have to carry; this is the traversal, which
     * needs nothing that is not already recorded.
     *
     * @param executionId the root to walk from
     * @return the root followed by its descendants, parents before children; empty if the id is unknown
     */
    List<CapabilityExecution> subtree(String executionId);
}
