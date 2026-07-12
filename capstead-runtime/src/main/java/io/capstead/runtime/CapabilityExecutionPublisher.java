package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Fans a completed {@link CapabilityExecution} out to every registered {@link CapabilityExecutionRecorder},
 * honouring the configured recording {@link Mode}.
 *
 * <p>Recording must never change the behaviour of the business call it observes, so the default is
 * {@link Mode#BEST_EFFORT}: recorders run inline but their failures are logged and swallowed. Choose
 * {@link Mode#SYNC} to let recorder failures surface (e.g. to fail closed on an audit sink), or
 * {@link Mode#ASYNC} to hand recording off to an executor and keep it off the request thread.
 */
public class CapabilityExecutionPublisher {

    private static final Logger log = System.getLogger(CapabilityExecutionPublisher.class.getName());

    /** How executions are delivered to recorders. */
    public enum Mode {
        /** Record inline; a recorder failure propagates to the caller. */
        SYNC,
        /** Record inline; a recorder failure is logged and swallowed (default). */
        BEST_EFFORT,
        /** Record on a separate executor; failures are logged and swallowed. */
        ASYNC
    }

    private final List<CapabilityExecutionRecorder> recorders;
    private final Mode mode;
    private final Executor executor;

    public CapabilityExecutionPublisher(List<CapabilityExecutionRecorder> recorders, Mode mode, Executor executor) {
        this.recorders = recorders == null ? List.of() : recorders;
        this.mode = mode == null ? Mode.BEST_EFFORT : mode;
        this.executor = executor;
    }

    /** Delivers the execution to all recorders according to the configured mode. */
    public void publish(CapabilityExecution execution) {
        if (recorders.isEmpty()) {
            return;
        }
        if (mode == Mode.ASYNC && executor != null) {
            executor.execute(() -> deliverBestEffort(execution));
            return;
        }
        if (mode == Mode.SYNC) {
            for (CapabilityExecutionRecorder recorder : recorders) {
                recorder.record(execution);
            }
            return;
        }
        deliverBestEffort(execution);
    }

    private void deliverBestEffort(CapabilityExecution execution) {
        for (CapabilityExecutionRecorder recorder : recorders) {
            try {
                recorder.record(execution);
            } catch (RuntimeException ex) {
                log.log(Level.WARNING, "[capstead] recorder {0} failed for execution '{1}': {2}",
                        recorder.getClass().getSimpleName(), execution.coordinates(), ex.getMessage());
            }
        }
    }
}
