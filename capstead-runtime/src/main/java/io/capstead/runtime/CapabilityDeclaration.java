package io.capstead.runtime;

import java.util.List;

/**
 * An explicit, configuration-driven capability declaration: bind a specific bean method to a
 * capability with an <em>exact</em> business name. Unlike scan rules, nothing is derived — the name
 * and governance metadata are given verbatim — which is what real client projects need, since they
 * can't be forced to name methods after their capabilities.
 *
 * <p>{@code parameterTypes} (fully-qualified or simple names) disambiguate overloaded methods; it may
 * be empty when the method name is unique on the bean.
 */
public final class CapabilityDeclaration {

    private final String beanName;
    private final String methodName;
    private final List<String> parameterTypes;
    private final String name;
    private final String domain;
    private final String owner;
    private final String version;
    private final List<String> tags;
    private final String dailyBudget;

    public CapabilityDeclaration(String beanName, String methodName, List<String> parameterTypes,
                                 String name, String domain, String owner, String version,
                                 List<String> tags, String dailyBudget) {
        this.beanName = beanName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        this.name = name;
        this.domain = domain == null ? "" : domain;
        this.owner = owner == null ? "" : owner;
        this.version = version == null || version.isBlank() ? "1" : version;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.dailyBudget = dailyBudget;
    }

    public String beanName() {
        return beanName;
    }

    public String methodName() {
        return methodName;
    }

    public List<String> parameterTypes() {
        return parameterTypes;
    }

    public String name() {
        return name;
    }

    public String domain() {
        return domain;
    }

    public String owner() {
        return owner;
    }

    public String version() {
        return version;
    }

    public List<String> tags() {
        return tags;
    }

    public String dailyBudget() {
        return dailyBudget;
    }
}
