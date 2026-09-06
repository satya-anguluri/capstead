package io.capstead.runtime;

import io.capstead.core.ExecutionAttributes;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The attribute names an application has declared it may set.
 *
 * <p>Structural validity is not permission. {@link ExecutionAttributes} says whether a name is well formed;
 * this says whether this application is allowed to use it. An undeclared name is not stored, so a typo or a
 * name invented in a hurry is caught at write time instead of becoming a column of near-duplicates that
 * somebody discovers in a dashboard six months later.
 *
 * <p><b>Declaring nothing allows nothing.</b> An empty registry rejects every attribute, which is the
 * behaviour that keeps an application that never opted in exactly as it was. The alternative — an empty
 * registry meaning "anything goes" — would make the allow-list a thing you have to remember to turn on, and
 * the whole point is that it is not optional.
 *
 * <p>Names are matched exactly. There is no prefix or wildcard form, deliberately: {@code policy.*} would
 * re-admit everything the registry exists to keep out, one namespace at a time.
 */
public final class ExecutionAttributeRegistry {

    private final Set<String> allowed;

    /** A registry allowing exactly these names. Malformed names are dropped rather than silently allowed. */
    public ExecutionAttributeRegistry(Set<String> names) {
        Set<String> valid = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (ExecutionAttributes.isValidName(name)) {
                    valid.add(name);
                }
            }
        }
        this.allowed = Collections.unmodifiableSet(valid);
    }

    /** A registry that allows nothing. */
    public static ExecutionAttributeRegistry empty() {
        return new ExecutionAttributeRegistry(Set.of());
    }

    public boolean allows(String name) {
        return allowed.contains(name);
    }

    /** The declared names, for diagnostics and for the actuator surface. */
    public Set<String> allowed() {
        return allowed;
    }

    public boolean isEmpty() {
        return allowed.isEmpty();
    }
}
