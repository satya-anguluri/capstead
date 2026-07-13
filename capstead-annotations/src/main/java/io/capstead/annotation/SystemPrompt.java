package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An optional system-message template for a declarative {@link CapabilityClient} method. Supports the
 * same <code>{{name}}</code> placeholders as {@link Prompt}.
 *
 * <pre>{@code
 * @SystemPrompt("You are a senior Java instructor. Be concise.")
 * @Prompt("Generate a lesson for {{topic}}")
 * Lesson execute(String topic);
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemPrompt {

    /** The system-message template, with {@code {{param}}} placeholders bound from method arguments. */
    String value();
}
