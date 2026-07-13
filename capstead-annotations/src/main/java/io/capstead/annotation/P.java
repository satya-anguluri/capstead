package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the template variable a parameter binds to in a declarative {@link CapabilityClient} method.
 *
 * <p>By default parameters bind by their compiled name (requires {@code -parameters}, which Spring
 * Boot enables by default). Use {@code @P} to bind to a different {@code {{name}}} placeholder or when
 * parameter names are not available.
 *
 * <pre>{@code
 * @Prompt("Summarize {{text}} in {{words}} words")
 * String summarize(@P("text") String content, @P("words") int limit);
 * }</pre>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface P {

    /** The template variable name this parameter binds to. */
    String value();
}
