package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

/**
 * Receives a completed {@link CapabilityExecution}.
 *
 * <p>Capstead fans each execution out to every registered recorder, so the in-memory store,
 * Micrometer, and (later) external sinks such as a database or event stream can each build on the
 * same first-class execution record. Implementations must be thread-safe.
 */
public interface CapabilityExecutionRecorder {

    void record(CapabilityExecution execution);
}
