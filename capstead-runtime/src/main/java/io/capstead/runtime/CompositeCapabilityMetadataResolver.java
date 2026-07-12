package io.capstead.runtime;

import io.capstead.core.CapabilityMetadata;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Chains several {@link CapabilityMetadataResolver}s, returning the first non-null result. Order
 * defines precedence — the annotation resolver is placed first so an explicit {@code @Capability}
 * always wins over a configuration-driven scan rule for the same method.
 */
public class CompositeCapabilityMetadataResolver implements CapabilityMetadataResolver {

    private final List<CapabilityMetadataResolver> delegates;

    public CompositeCapabilityMetadataResolver(List<CapabilityMetadataResolver> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public CapabilityMetadata resolve(Method method) {
        for (CapabilityMetadataResolver delegate : delegates) {
            CapabilityMetadata metadata = delegate.resolve(method);
            if (metadata != null) {
                return metadata;
            }
        }
        return null;
    }

    @Override
    public String dailyBudget(Method method) {
        for (CapabilityMetadataResolver delegate : delegates) {
            String budget = delegate.dailyBudget(method);
            if (budget != null) {
                return budget;
            }
        }
        return null;
    }
}
