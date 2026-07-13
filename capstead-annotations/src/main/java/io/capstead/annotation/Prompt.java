package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The user-message prompt template for a declarative {@link CapabilityClient} method.
 *
 * <p>The template uses <code>{{name}}</code> placeholders that are bound from the method's
 * parameters — by parameter name, or by {@link P} when an explicit name is preferred:
 *
 * <pre>{@code
 * @Prompt("Generate a Java lesson for {{topic}} at {{level}} level")
 * Lesson execute(String topic, @P("level") String difficulty);
 * }</pre>
 *
 * <p>Text blocks are the idiomatic way to write multi-line prompts.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prompt {

    /** The prompt template, with {@code {{param}}} placeholders bound from method arguments. */
    String value();
}
