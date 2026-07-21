package io.capstead.runtime;

/**
 * A configuration-declared usage metering rule: how to derive billable input units for a capability
 * <em>without any client-code enrichment</em>. Some model calls aren't token-billed LLM chats — TTS is
 * billed per character, transcription per second, some APIs per request — and the code making them
 * often can't (or shouldn't) be edited to call
 * {@link CapabilityExecutionContext#recordModelInvocation}. A usage rule lets the declaration say
 * "the billable input is the length of argument N", and the interceptor records one model invocation
 * from that measurement, which the {@link TokenCostEstimator} then prices from {@code capstead.cost}.
 *
 * <p>Declared usage is a fallback, never an override: if the execution already carries model
 * invocations (real enrichment from inside the call), the rule is ignored.
 *
 * @param model        the model identifier to record (and price), e.g. {@code elevenlabs/eleven_multilingual_v2}
 * @param unit         what one input unit means; measurement is the declared argument's
 *                     {@link CharSequence#length()} (or {@link Number} value), except
 *                     {@link Unit#REQUESTS} which is always 1 per invocation
 * @param inputFromArg zero-based index of the method argument the input units are measured from
 */
public record CapabilityUsageRule(String model, Unit unit, int inputFromArg) {

    /** The billing unit the model's {@code capstead.cost} rate is expressed in. */
    public enum Unit { TOKENS, CHARACTERS, SECONDS, REQUESTS }

    public CapabilityUsageRule {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("usage.model is required");
        }
        if (unit == null) {
            unit = Unit.CHARACTERS;
        }
        if (inputFromArg < 0) {
            throw new IllegalArgumentException("usage.input-from-arg must be >= 0");
        }
    }

    /**
     * Measures the billable input units of one invocation, or {@code -1} when the declared argument
     * isn't measurable (missing index, or neither a {@link CharSequence} nor a {@link Number}).
     */
    public int measure(Object[] arguments) {
        if (unit == Unit.REQUESTS) {
            return 1;
        }
        if (arguments == null || inputFromArg >= arguments.length) {
            return -1;
        }
        Object argument = arguments[inputFromArg];
        if (argument instanceof CharSequence text) {
            return text.length();
        }
        if (argument instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }
}
