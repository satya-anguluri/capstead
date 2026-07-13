package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the model profile a declarative {@link CapabilityClient} method should run on. The name
 * resolves to a profile under {@code capstead.ai.profiles.<name>} (model, temperature, max-tokens …).
 *
 * <pre>{@code
 * capstead:
 *   ai:
 *     profiles:
 *       reasoning: { model: us.anthropic.claude-sonnet-4-6, temperature: 0.2 }
 *       fast:      { model: amazon.nova-pro }
 * }</pre>
 *
 * <p>May be placed on a method (highest precedence) or on the interface (default for all methods).
 * When absent, or when the named profile is not configured, the application's default Spring AI
 * model is used.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelProfile {

    /** The profile name, resolved against {@code capstead.ai.profiles.<name>}. */
    String value();
}
