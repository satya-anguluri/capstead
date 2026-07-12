package io.capstead.runtime;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityMetadata;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves and instruments {@link CapabilityDeclaration}s: binds each declared bean method to an
 * exact capability name, serves as a {@link CapabilityMetadataResolver} for interception, and (as a
 * {@link SmartInitializingSingleton}) registers the descriptors so declared capabilities appear in
 * the catalog — all with <strong>no source changes</strong> to the declared beans.
 *
 * <p>Binding is <em>fail-fast</em>: a missing bean, a missing method, or an ambiguous overload is a
 * startup error with an actionable message, so mis-configuration is caught immediately. Bean types
 * are resolved via {@code getType} (no bean instantiation), so this is safe to build early for the
 * AOP advisor.
 */
public class ExplicitCapabilityMetadataResolver implements CapabilityMetadataResolver, SmartInitializingSingleton {

    private final ApplicationContext context;
    private final CapabilityRegistry registry;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final Map<Method, CapabilityMetadata> metadataByMethod = new HashMap<>();
    private final Map<Method, String> budgetByMethod = new HashMap<>();
    private final Map<Method, String> beanNameByMethod = new HashMap<>();

    public ExplicitCapabilityMetadataResolver(ApplicationContext context,
                                              CapabilityRegistry registry,
                                              List<CapabilityDeclaration> declarations) {
        this.context = context;
        this.registry = registry;
        for (CapabilityDeclaration declaration : declarations) {
            bind(declaration);
        }
    }

    private void bind(CapabilityDeclaration declaration) {
        require(declaration.name(), "capstead.capabilities: 'name' is required");
        require(declaration.beanName(),
                "capstead.capabilities['" + declaration.name() + "']: 'bean' is required");
        require(declaration.methodName(),
                "capstead.capabilities['" + declaration.name() + "']: 'method' is required");

        Class<?> type;
        try {
            type = context.getType(declaration.beanName());
        } catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException(
                    "capstead.capabilities['" + declaration.name() + "']: no bean named '"
                            + declaration.beanName() + "'", ex);
        }
        if (type == null) {
            throw new IllegalStateException("capstead.capabilities['" + declaration.name()
                    + "']: could not determine the type of bean '" + declaration.beanName() + "'");
        }
        Class<?> userClass = ClassUtils.getUserClass(type);
        Method method = findMethod(userClass, declaration);

        CapabilityMetadata metadata = new CapabilityMetadata(
                declaration.name(), "", declaration.domain(), declaration.owner(),
                declaration.version(), declaration.tags());

        CapabilityMetadata previous = metadataByMethod.putIfAbsent(method, metadata);
        if (previous != null) {
            throw new IllegalStateException("capstead.capabilities: method '" + declaration.methodName()
                    + "' on bean '" + declaration.beanName() + "' is declared more than once ('"
                    + previous.name() + "' and '" + declaration.name() + "')");
        }
        if (declaration.dailyBudget() != null) {
            budgetByMethod.put(method, declaration.dailyBudget());
        }
        beanNameByMethod.put(method, declaration.beanName());
    }

    private Method findMethod(Class<?> userClass, CapabilityDeclaration declaration) {
        List<Method> matches = new ArrayList<>();
        for (Method method : userClass.getMethods()) {
            if (!method.getName().equals(declaration.methodName())) {
                continue;
            }
            if (!declaration.parameterTypes().isEmpty() && !parameterTypesMatch(method, declaration.parameterTypes())) {
                continue;
            }
            matches.add(method);
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("capstead.capabilities['" + declaration.name() + "']: no public method '"
                    + declaration.methodName() + "'"
                    + (declaration.parameterTypes().isEmpty() ? "" : " with parameter-types " + declaration.parameterTypes())
                    + " on bean '" + declaration.beanName() + "' (" + userClass.getName() + ")");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("capstead.capabilities['" + declaration.name() + "']: method '"
                    + declaration.methodName() + "' on bean '" + declaration.beanName()
                    + "' is overloaded; add 'parameter-types' to disambiguate");
        }
        return matches.get(0);
    }

    private boolean parameterTypesMatch(Method method, List<String> parameterTypeNames) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != parameterTypeNames.size()) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            String want = parameterTypeNames.get(i).trim();
            if (!actual[i].getName().equals(want) && !actual[i].getSimpleName().equals(want)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CapabilityMetadata resolve(Method method) {
        return metadataByMethod.get(method);
    }

    @Override
    public String dailyBudget(Method method) {
        return budgetByMethod.get(method);
    }

    /** The set of declared methods, used to build the interception pointcut. */
    public Set<Method> methods() {
        return metadataByMethod.keySet();
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Map.Entry<Method, CapabilityMetadata> entry : metadataByMethod.entrySet()) {
            Method method = entry.getKey();
            CapabilityMetadata metadata = entry.getValue();
            if (MergedAnnotations.from(method).isPresent(Capability.class)) {
                continue; // the annotation path owns annotated methods
            }
            if (registry.find(metadata.coordinates()).isPresent()) {
                continue; // already registered under these coordinates
            }
            Object bean = context.getBean(beanNameByMethod.get(method));
            registry.register(new CapabilityDescriptor(metadata, bean, method, resolveParameterNames(method)));
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private String[] resolveParameterNames(Method method) {
        String[] discovered = parameterNameDiscoverer.getParameterNames(method);
        if (discovered != null) {
            return discovered;
        }
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }
}
