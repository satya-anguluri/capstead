package io.capstead.starter;

import io.capstead.runtime.CapabilityExecutionPublisher;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls how capability executions are recorded and what they capture.
 *
 * <p>Example:
 * <pre>
 * capstead.executions.recording-mode: best-effort   # best-effort (default) | sync | async
 * capstead.executions.max-history: 500
 * capstead.executions.capture-input: false
 * capstead.executions.capture-output: false
 * capstead.executions.capture-max-length: 2000
 * </pre>
 */
@ConfigurationProperties("capstead.executions")
public class CapsteadExecutionsProperties {

    /** How executions are delivered to recorders. Defaults to best-effort (never fails the call). */
    private CapabilityExecutionPublisher.Mode recordingMode = CapabilityExecutionPublisher.Mode.BEST_EFFORT;

    /** Bounded size of the in-memory execution history. */
    private int maxHistory = 200;

    /** Capture a (redacted) summary of the capability input. Off by default for privacy. */
    private boolean captureInput = false;

    /** Capture a (redacted) summary of the capability output. Off by default for privacy. */
    private boolean captureOutput = false;

    /** Maximum length of a captured input/output before truncation. */
    private int captureMaxLength = 2000;

    public CapabilityExecutionPublisher.Mode getRecordingMode() {
        return recordingMode;
    }

    public void setRecordingMode(CapabilityExecutionPublisher.Mode recordingMode) {
        this.recordingMode = recordingMode;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public boolean isCaptureInput() {
        return captureInput;
    }

    public void setCaptureInput(boolean captureInput) {
        this.captureInput = captureInput;
    }

    public boolean isCaptureOutput() {
        return captureOutput;
    }

    public void setCaptureOutput(boolean captureOutput) {
        this.captureOutput = captureOutput;
    }

    public int getCaptureMaxLength() {
        return captureMaxLength;
    }

    public void setCaptureMaxLength(int captureMaxLength) {
        this.captureMaxLength = captureMaxLength;
    }
}
