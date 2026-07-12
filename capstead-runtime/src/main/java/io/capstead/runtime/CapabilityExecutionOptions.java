package io.capstead.runtime;

/**
 * Immutable capture/attribution settings applied by {@link CapabilityMethodInterceptor} to each
 * execution: whether to capture the (redacted) input and output, the redactor to apply, and the
 * provider used to attribute the execution to a principal.
 */
public final class CapabilityExecutionOptions {

    private final boolean captureInput;
    private final boolean captureOutput;
    private final int captureMaxLength;
    private final CapabilityDataRedactor redactor;
    private final CapabilityPrincipalProvider principalProvider;

    public CapabilityExecutionOptions(boolean captureInput, boolean captureOutput, int captureMaxLength,
                                      CapabilityDataRedactor redactor, CapabilityPrincipalProvider principalProvider) {
        this.captureInput = captureInput;
        this.captureOutput = captureOutput;
        this.captureMaxLength = captureMaxLength <= 0 ? 2000 : captureMaxLength;
        this.redactor = redactor == null ? new NoOpCapabilityDataRedactor() : redactor;
        this.principalProvider = principalProvider;
    }

    /** Metadata-only defaults: no input/output capture, no-op redactor, no principal attribution. */
    public static CapabilityExecutionOptions defaults() {
        return new CapabilityExecutionOptions(false, false, 2000, new NoOpCapabilityDataRedactor(), null);
    }

    public boolean captureInput() {
        return captureInput;
    }

    public boolean captureOutput() {
        return captureOutput;
    }

    public int captureMaxLength() {
        return captureMaxLength;
    }

    public CapabilityDataRedactor redactor() {
        return redactor;
    }

    public CapabilityPrincipalProvider principalProvider() {
        return principalProvider;
    }
}
