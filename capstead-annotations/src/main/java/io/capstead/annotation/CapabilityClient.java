package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an <strong>interface</strong> as a declarative Capstead capability client: Capstead supplies
 * the implementation at runtime, so the interface methods have <em>no body</em>.
 *
 * <p>Each method annotated with {@link Capability} and {@link Prompt} becomes a fully governed
 * capability — Capstead renders the prompt, routes to the model resolved from {@link ModelProfile},
 * calls the model (via Spring AI's {@code ChatClient}), binds the response to the method's return
 * type, and records the whole thing as a first-class execution (scorecard, tokens, cost, tree).
 *
 * <pre>{@code
 * @CapabilityClient
 * public interface LessonCapability {
 *
 *     @Capability(name = "Generate Lesson", domain = "Learning")
 *     @ModelProfile("reasoning")
 *     @Prompt("Generate a Java lesson for {{topic}}")
 *     Lesson execute(String topic);
 * }
 * }</pre>
 *
 * <p>Interfaces are discovered automatically within the application's auto-configuration base
 * packages — no {@code @Enable} annotation or manual registration required.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CapabilityClient {

    /**
     * Optional default {@link ModelProfile} name applied to every method on the interface that does
     * not declare its own. Empty means "use the application's default Spring AI model".
     */
    String profile() default "";
}
