package io.capstead.runtime;

/**
 * The provider-neutral seam that lets <em>any</em> project use declarative capabilities
 * ({@code @CapabilityClient}) — with or without Spring AI.
 *
 * <p>A declarative capability method has no body. Capstead renders its prompt, resolves the model
 * profile, and calls this invoker; the application supplies the implementation that actually talks to
 * a model. Capstead then binds the returned text to the method's return type and records the whole
 * call as a governed execution.
 *
 * <p>Provide exactly one bean:
 *
 * <pre>{@code
 * @Bean
 * CapabilityModelInvoker modelInvoker(MyLlmClient llm) {
 *     return request -> llm.complete(request.model(), request.systemPrompt(), request.userPrompt());
 * }
 * }</pre>
 *
 * <p>If {@code capstead-spring-ai} is on the classpath, a default Spring AI {@code ChatClient}-backed
 * invoker is auto-registered, so Spring AI users need no bean at all.
 */
@FunctionalInterface
public interface CapabilityModelInvoker {

    /**
     * Runs one model call and returns the model's raw text response.
     *
     * @param request the rendered prompts, resolved model/profile, and return type
     * @return the model's text (Capstead binds it to the capability's return type)
     */
    String invoke(CapabilityModelRequest request);
}
