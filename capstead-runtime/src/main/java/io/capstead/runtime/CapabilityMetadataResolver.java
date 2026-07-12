package io.capstead.runtime;

import io.capstead.core.CapabilityMetadata;

import java.lang.reflect.Method;

/**
 * Resolves the governance metadata (and optional daily budget) for a capability method, decoupling
 * the interceptor from <em>how</em> a method is declared a capability.
 *
 * <p>Implementations may read the {@code @Capability} annotation, a configuration-driven scan rule,
 * an explicit declaration, or any other source. Returning {@code null} from {@link #resolve(Method)}
 * means the method is not a capability and should be invoked without instrumentation.
 */
public interface CapabilityMetadataResolver {

    /** Metadata for the capability method, or {@code null} if the method is not a capability. */
    CapabilityMetadata resolve(Method method);

    /** The daily-budget spec (e.g. {@code "$25"}) for the method, or {@code null} if none. */
    default String dailyBudget(Method method) {
        return null;
    }
}
