package io.capstead.runtime;

/**
 * Supplies the principal (caller) a capability execution runs for, so executions can be attributed to
 * a user, tenant or service.
 *
 * <p>Pluggable: the default returns {@code null} (unattributed). Applications provide a bean that
 * reads their own security context (e.g. Spring Security's {@code SecurityContextHolder}).
 */
@FunctionalInterface
public interface CapabilityPrincipalProvider {

    /** The current principal, or {@code null} when there is none. */
    String currentPrincipal();
}
