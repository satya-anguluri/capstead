package io.capstead.runtime;

/**
 * Redacts a captured capability input/output before it is stored on a {@link io.capstead.core.CapabilityExecution}.
 *
 * <p>Capture is off by default; when an application opts in to capturing inputs or outputs, provide a
 * redactor bean to strip secrets or PII. The default {@link NoOpCapabilityDataRedactor} returns the
 * value unchanged.
 */
@FunctionalInterface
public interface CapabilityDataRedactor {

    /** Returns a safe-to-store representation of {@code value}, or {@code null} for {@code null}. */
    String redact(String value);
}
