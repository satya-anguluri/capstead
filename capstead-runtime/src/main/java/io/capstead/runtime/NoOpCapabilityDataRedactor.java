package io.capstead.runtime;

/** Default redactor that stores captured values unchanged. */
public class NoOpCapabilityDataRedactor implements CapabilityDataRedactor {

    @Override
    public String redact(String value) {
        return value;
    }
}
