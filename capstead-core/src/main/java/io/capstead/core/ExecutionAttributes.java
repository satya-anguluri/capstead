package io.capstead.core;

import java.util.regex.Pattern;

/**
 * The structural rules for execution attributes.
 *
 * <p>Attributes let an execution record <em>what it decided</em> and which versioned inputs produced that
 * decision — a policy outcome, the revision of the source it acted on, a prompt or fixture version. The
 * lifecycle fields on {@link CapabilityExecution} answer <em>how it went</em>; overloading them to answer
 * this would be worse than adding nothing.
 *
 * <p>The rules here are the ones that must hold wherever an attribute is set, so they live in core with no
 * dependency on Spring or configuration. Which <em>names</em> an application may use is a separate, wider
 * question answered by the allow-list registry in the runtime — a name can be structurally valid and still
 * not be permitted.
 *
 * <h2>Why constrained at all</h2>
 *
 * <p>This map hangs off the atom that Actuator, Micrometer, dashboards, cost reports and budgets all derive
 * from. A free-form map on that atom becomes a dumping ground, and the damage shows up far from the code
 * that caused it. So the framework constrains it rather than a convention.
 *
 * <h2>Attributes are NOT metric dimensions</h2>
 *
 * <p>The trap worth designing against up front: an attribute like a run or trace identifier promoted to a
 * Micrometer tag would multiply the time series by its cardinality and can take down a metrics backend. The
 * separation is structural rather than documented — {@code CapabilityMetrics} builds a fixed four-tag set
 * from explicit fields and cannot see attributes at all. {@code MicrometerAttributeIsolationTest} asserts
 * that, so adding attributes to a tag later fails a test rather than a production Prometheus.
 */
public final class ExecutionAttributes {

    /**
     * Attribute names are namespaced: at least one dot, so {@code evidence.state} is a name and
     * {@code state} is not.
     *
     * <p>The namespace is what keeps two unrelated domains from colliding on a short word, and it makes an
     * attribute's owner readable at the point it is queried.
     *
     * <p>The root segment must start lower-case, so a name reads as a namespace rather than a class name.
     * Segments then allow letters, digits, underscores and hyphens — camelCase included, because
     * {@code evidence.sourceRevision} is the natural spelling and a rule that forbade it would be a rule
     * people work around. Deliberately no spaces, quotes, wildcards or dots inside a segment, because these
     * names end up in SQL predicates and JSON keys.
     */
    public static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9_-]*(\\.[a-zA-Z0-9_-]+)+");

    /** Long enough for {@code policy.authorization.outcome}; short enough to index. */
    public static final int MAX_NAME_LENGTH = 120;

    /** A sanitized reason code, a revision or a version — not a document. */
    public static final int MAX_VALUE_LENGTH = 512;

    /**
     * A ceiling per execution, so a loop that sets an attribute per iteration cannot grow one record without
     * bound. Generous for describing a decision; far below anything that would hurt.
     */
    public static final int MAX_PER_EXECUTION = 32;

    private ExecutionAttributes() {
    }

    /** Whether {@code name} is a structurally valid attribute name. Says nothing about whether it is allowed. */
    public static boolean isValidName(String name) {
        return name != null
                && name.length() <= MAX_NAME_LENGTH
                && NAME.matcher(name).matches();
    }

    /** Whether {@code value} may be stored as-is. Null and over-length values are not stored. */
    public static boolean isValidValue(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_VALUE_LENGTH;
    }
}
