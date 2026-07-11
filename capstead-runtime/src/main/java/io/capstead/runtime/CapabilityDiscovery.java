package io.capstead.runtime;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityMetadata;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * Scans the application context once at startup, discovers every {@code @Capability} method,
 * validates it and registers a {@link CapabilityDescriptor}.
 *
 * <p>Runs as a {@link SmartInitializingSingleton} so all beans exist before discovery. For each
 * bean it inspects the user (unproxied) class, selects annotated methods, runs the
 * {@link CapabilitySignatureValidator}, resolves business parameter names, and populates the
 * {@link CapabilityRegistry}. Any problem fails application startup, which is the intended
 * "fast, obvious failure" behavior for a Spring Boot starter.
 */
public final class CapabilityDiscovery implements SmartInitializingSingleton {

    private final ApplicationContext context;
    private final CapabilityRegistry registry;
    private final CapabilitySignatureValidator validator;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CapabilityDiscovery(ApplicationContext context,
                               CapabilityRegistry registry,
                               CapabilitySignatureValidator validator) {
        this.context = context;
        this.registry = registry;
        this.validator = validator;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> userClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(userClass, method -> tryRegister(bean, userClass, method));
        }
    }

    private void tryRegister(Object bean, Class<?> userClass, Method method) {
        if (!MergedAnnotations.from(method).isPresent(Capability.class)) {
            return;
        }
        Capability annotation = AnnotatedElementUtils.findMergedAnnotation(method, Capability.class);
        String name = annotation.name();

        validator.validate(name, method);

        CapabilityMetadata metadata = new CapabilityMetadata(
                name,
                annotation.description(),
                annotation.domain(),
                annotation.owner(),
                annotation.version(),
                Arrays.asList(annotation.tags()));
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                metadata,
                bean,
                method,
                resolveParameterNames(method));
        registry.register(descriptor);
    }

    private String[] resolveParameterNames(Method method) {
        String[] discovered = parameterNameDiscoverer.getParameterNames(method);
        if (discovered != null) {
            return discovered;
        }
        // Fallback when compiled without -parameters and no debug info: arg0, arg1, ...
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }
}
