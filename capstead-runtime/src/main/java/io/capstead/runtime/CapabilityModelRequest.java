package io.capstead.runtime;

import java.lang.reflect.Type;

/**
 * A single model call requested by a declarative capability, in provider-neutral terms.
 *
 * <p>Capstead renders the prompts and resolves the model profile, then hands this request to a
 * {@link CapabilityModelInvoker}. The invoker calls whatever backend the application uses (Spring AI,
 * LangChain4j, a provider SDK, or a plain HTTP client) and returns the model's raw text — Capstead
 * binds that text to the capability's return type.
 *
 * @param systemPrompt      the rendered system message, or {@code null} if none
 * @param userPrompt        the rendered user message (already includes any output-format instruction)
 * @param model             the model id from the selected profile, or {@code null} to use the backend default
 * @param temperature       sampling temperature from the profile, or {@code null}
 * @param maxTokens         max output tokens from the profile, or {@code null}
 * @param topP              nucleus-sampling top-p from the profile, or {@code null}
 * @param returnType        the capability method's return type (for reference; Capstead performs binding)
 * @param capabilityName    the business capability name (for logging/routing), or {@code null}
 */
public record CapabilityModelRequest(
        String systemPrompt,
        String userPrompt,
        String model,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Type returnType,
        String capabilityName) {

    /** {@code true} when the capability expects structured output (anything other than a raw String). */
    public boolean expectsStructuredOutput() {
        return returnType != null && returnType != String.class
                && returnType != void.class && returnType != Void.class;
    }
}
