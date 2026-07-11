package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean method as a business capability managed by Capstead.
 *
 * <p>Capstead does <em>not</em> execute the capability's AI logic — the method body does that
 * (typically via Spring AI or LangChain4j). Capstead owns everything <em>around</em> the
 * capability: registration, metadata, discovery, versioning, policy, and observability.
 *
 * <p>The annotation carries governance metadata that Capstead exposes (e.g. via
 * {@code GET /actuator/capabilities}) and uses for routing and cataloging.
 *
 * <pre>{@code
 * @Capability(
 *     name = "Generate Lesson",
 *     domain = "EngineerPrep",
 *     owner = "Content Team",
 *     version = "2",
 *     tags = {"lesson", "java"}
 * )
 * public Lesson generateLesson(String topic) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {

    /**
     * Human-readable business name (e.g. {@code "Generate Lesson"}). Unique per version within
     * the application context; used for discovery, routing, and metrics tags.
     */
    String name();

    /** Optional business description surfaced in the catalog and dashboard. */
    String description() default "";

    /** Owning business domain (e.g. {@code "EngineerPrep"}). Used for grouping and cataloging. */
    String domain() default "";

    /** Owning team or individual accountable for the capability. */
    String owner() default "";

    /** Capability version, enabling side-by-side routing (e.g. {@code "1"} and {@code "2"}). */
    String version() default "1";

    /** Free-form tags for search and filtering. */
    String[] tags() default {};
}
