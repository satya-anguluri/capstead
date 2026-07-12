package io.capstead.runtime;

import io.capstead.core.CapabilityMetadata;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves capability metadata from configuration-driven {@link CapabilityScanRule}s, deriving the
 * name via a {@link CapabilityNamingStrategy}. This is how methods become governed capabilities with
 * zero source changes. Matches are cached per method.
 */
public class ScanRuleCapabilityMetadataResolver implements CapabilityMetadataResolver {

    private final List<CapabilityScanRule> rules;
    private final CapabilityNamingStrategy namingStrategy;
    private final ConcurrentHashMap<Method, Optional<CapabilityScanRule>> cache = new ConcurrentHashMap<>();

    public ScanRuleCapabilityMetadataResolver(List<CapabilityScanRule> rules,
                                              CapabilityNamingStrategy namingStrategy) {
        this.rules = List.copyOf(rules);
        this.namingStrategy = namingStrategy;
    }

    private Optional<CapabilityScanRule> match(Method method) {
        return cache.computeIfAbsent(method, m -> rules.stream()
                .filter(rule -> rule.matches(m.getDeclaringClass(), m))
                .findFirst());
    }

    @Override
    public CapabilityMetadata resolve(Method method) {
        return match(method)
                .map(rule -> new CapabilityMetadata(
                        namingStrategy.toName(method), "", rule.domain(), rule.owner(), rule.version(), rule.tags()))
                .orElse(null);
    }

    @Override
    public String dailyBudget(Method method) {
        return match(method).map(CapabilityScanRule::dailyBudget).orElse(null);
    }
}
