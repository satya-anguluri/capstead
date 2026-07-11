package io.capstead.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Caps a capability's estimated spend per day.
 *
 * <p>Capstead tracks each capability's daily cost (from token usage × configured model rates). Once
 * the accumulated spend for the current day reaches the budget, further invocations are blocked with
 * a {@code CapabilityBudgetException} until the next day. This turns the governance layer from
 * observational into active.
 *
 * <pre>{@code
 * @Capability(name = "Generate Lesson")
 * @DailyBudget("$25")
 * public Lesson generateLesson(String topic) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DailyBudget {

    /**
     * The per-day budget, e.g. {@code "$25"}, {@code "25"} or {@code "0.50"}. Currency symbols and
     * separators are ignored; only the numeric amount is used.
     */
    String value();
}
