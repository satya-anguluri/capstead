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
}
