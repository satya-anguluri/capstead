package io.capstead.runtime;

import io.capstead.core.CapabilityMetadata;

import java.lang.reflect.Method;

/**
 * Immutable record of a discovered {@code @Capability}: its governance metadata plus the bean
 * method that backs it.
 *
 * <p>Capstead keeps the {@link Method} handle not to execute AI, but so the management layer can
 * observe, route, apply policy to, and version the capability. The AI logic lives inside the
 * method body (typically calling Spring AI or LangChain4j) and is out of Capstead's scope.
 */
public final class CapabilityDescriptor {

    private final CapabilityMetadata metadata;
    private final Object bean;
    private final Method method;
    private final String[] parameterNames;

    public CapabilityDescriptor(CapabilityMetadata metadata,
                                Object bean,
                                Method method,
                                String[] parameterNames) {
        this.metadata = metadata;
        this.bean = bean;
        this.method = method;
        this.parameterNames = parameterNames.clone();
    }

    /** Governance metadata (name, domain, owner, version, tags). */
    public CapabilityMetadata metadata() {
        return metadata;
    }

    /** Convenience accessor for the business name. */
    public String name() {
        return metadata.name();
    }

    /** Stable identity across versions: {@code name@version}. */
    public String coordinates() {
        return metadata.coordinates();
    }

    /** The bean instance backing this capability. */
    public Object bean() {
        return bean;
    }

    /** The reflective handle to the capability method. */
    public Method method() {
        return method;
    }

    /** Business parameter names, in declaration order. */
    public String[] parameterNames() {
        return parameterNames.clone();
    }
}
