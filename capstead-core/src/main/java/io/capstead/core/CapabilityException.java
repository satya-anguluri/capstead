package io.capstead.core;

/**
 * Thrown when a capability cannot be registered or is misconfigured.
 *
 * <p>Capstead is a management layer, so its failures are about the capability catalog itself
 * (duplicate coordinates, invalid signatures), not about AI execution — that concern belongs to
 * the underlying engine.
 */
public class CapabilityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Classification of a capability management failure. */
    public enum Reason {
        /** Two capabilities declared the same {@code name@version} coordinates. */
        DUPLICATE_COORDINATES,
        /** A capability's declaration or wiring was invalid. */
        CONFIGURATION_ERROR
    }

    private final Reason reason;
    private final String capabilityName;

    public CapabilityException(Reason reason, String capabilityName, String message) {
        super(message);
        this.reason = reason;
        this.capabilityName = capabilityName;
    }

    public Reason reason() {
        return reason;
    }

    public String capabilityName() {
        return capabilityName;
    }
}
