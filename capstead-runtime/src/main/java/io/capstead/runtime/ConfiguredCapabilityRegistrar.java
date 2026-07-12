package io.capstead.runtime;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityMetadata;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;

/**
 * Registers {@link CapabilityDescriptor}s for every bean method matched by a
 * {@link CapabilityScanRule}, so scanned capabilities appear in the catalog and dashboard just like
 * annotated ones — with no source changes to the scanned beans.
 *
 * <p>Runs after all singletons exist. Methods that carry {@code @Capability} are skipped (the
 * annotation path owns them), and duplicate {@code name@version} coordinates are skipped so
 * scanning never fails startup.
 */
public final class ConfiguredCapabilityRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext context;
    private final CapabilityRegistry registry;
    private final List<CapabilityScanRule> rules;
    private final CapabilityNamingStrategy namingStrategy;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ConfiguredCapabilityRegistrar(ApplicationContext context,
                                         CapabilityRegistry registry,
                                         List<CapabilityScanRule> rules,
                                         CapabilityNamingStrategy namingStrategy) {
        this.context = context;
        this.registry = registry;
        this.rules = List.copyOf(rules);
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (rules.isEmpty()) {
            return;
        }
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = context.getBean(beanName);
            } catch (RuntimeException ex) {
                continue; // non-instantiable / lazy-failing bean — skip
            }
            Class<?> userClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(userClass,
                    method -> register(bean, userClass, method),
                    method -> isCandidate(userClass, method));
        }
    }

    private boolean isCandidate(Class<?> userClass, Method method) {
        if (method.getDeclaringClass() == Object.class) {
            return false;
        }
        if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
            return false;
        }
        if (MergedAnnotations.from(method).isPresent(Capability.class)) {
            return false; // the annotation path owns annotated methods
        }
        return firstMatch(userClass, method) != null;
    }

    private void register(Object bean, Class<?> userClass, Method method) {
        CapabilityScanRule rule = firstMatch(userClass, method);
        if (rule == null) {
            return;
        }
        CapabilityMetadata metadata = new CapabilityMetadata(
                namingStrategy.toName(method), "", rule.domain(), rule.owner(), rule.version(), rule.tags());
        if (registry.find(metadata.coordinates()).isPresent()) {
            return; // already registered (annotation, or another rule/method with the same name)
        }
        registry.register(new CapabilityDescriptor(metadata, bean, method, resolveParameterNames(method)));
    }

    private CapabilityScanRule firstMatch(Class<?> userClass, Method method) {
        for (CapabilityScanRule rule : rules) {
            if (rule.matches(userClass, method)) {
                return rule;
            }
        }
        return null;
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
