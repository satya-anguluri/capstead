package io.capstead.springai;

import io.capstead.annotation.Capability;
import io.capstead.annotation.CapabilityClient;
import io.capstead.core.CapabilityMetadata;
import io.capstead.runtime.CapabilityDescriptor;
import io.capstead.runtime.CapabilityRegistry;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers declarative {@link CapabilityClient} methods into the {@link CapabilityRegistry} so they
 * appear in the catalog ({@code /actuator/capabilities}), dashboard and domain grouping alongside
 * annotation- and config-declared capabilities.
 *
 * <p>Standard {@code CapabilityDiscovery} cannot see these: a JDK proxy's methods do not carry the
 * interface's annotations. This registrar closes that gap by reading the {@code @Capability} metadata
 * straight off the client interface. It runs after all singletons are created and is idempotent
 * (coordinates already present are skipped), so it never conflicts with regular discovery.
 */
public class DeclarativeCapabilityCatalogRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    public DeclarativeCapabilityCatalogRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        CapabilityRegistry registry = applicationContext.getBeanProvider(CapabilityRegistry.class).getIfAvailable();
        if (registry == null) {
            return;
        }
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }
            for (Class<?> candidate : candidateInterfaces(beanType)) {
                registerInterface(registry, candidate, applicationContext.getBean(beanName));
            }
        }
    }

    /**
     * The {@code @CapabilityClient} interfaces a bean exposes. Includes the bean type itself when it
     * <em>is</em> such an interface (the usual case for a proxy bean, whose type is the client
     * interface) — {@link ClassUtils#getAllInterfacesForClass} only returns super-interfaces.
     */
    private static Set<Class<?>> candidateInterfaces(Class<?> beanType) {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        if (beanType.isInterface() && beanType.isAnnotationPresent(CapabilityClient.class)) {
            candidates.add(beanType);
        }
        for (Class<?> iface : ClassUtils.getAllInterfacesForClass(beanType)) {
            if (iface.isAnnotationPresent(CapabilityClient.class)) {
                candidates.add(iface);
            }
        }
        return candidates;
    }

    private void registerInterface(CapabilityRegistry registry, Class<?> clientInterface, Object bean) {
        for (Method method : clientInterface.getMethods()) {
            Capability annotation = AnnotatedElementUtils.findMergedAnnotation(method, Capability.class);
            if (annotation == null) {
                continue;
            }
            CapabilityMetadata metadata = new CapabilityMetadata(
                    annotation.name(),
                    annotation.description(),
                    annotation.domain(),
                    annotation.owner(),
                    annotation.version(),
                    Arrays.asList(annotation.tags()));
            if (registry.find(metadata.coordinates()).isPresent()) {
                continue;
            }
            registry.register(new CapabilityDescriptor(metadata, bean, method, parameterNames(method)));
        }
    }

    private static String[] parameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }
}
