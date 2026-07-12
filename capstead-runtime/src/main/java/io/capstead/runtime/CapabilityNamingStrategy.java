package io.capstead.runtime;

import java.lang.reflect.Method;

/**
 * Derives a human-readable capability name from a bean method — the key that lets convention-based
 * scanning name many capabilities without an explicit list.
 */
public interface CapabilityNamingStrategy {

    /** The business name for the capability backed by {@code method}. */
    String toName(Method method);
}
