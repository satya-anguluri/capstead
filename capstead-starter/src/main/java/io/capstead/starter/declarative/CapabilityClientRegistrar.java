package io.capstead.starter.declarative;

import io.capstead.annotation.CapabilityClient;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.List;

/**
 * Scans the application's auto-configuration base packages for {@link CapabilityClient} interfaces and
 * registers a {@link CapabilityClientFactoryBean} for each — the declarative analogue of how Spring
 * Data discovers repositories. No {@code @Enable} annotation is required.
 */
public class CapabilityClientRegistrar
        implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, EnvironmentAware, ResourceLoaderAware {

    private BeanFactory beanFactory;
    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!AutoConfigurationPackages.has(beanFactory)) {
            return;
        }
        List<String> basePackages = AutoConfigurationPackages.get(beanFactory);
        if (basePackages.isEmpty()) {
            return;
        }

        ClassPathScanningCandidateComponentProvider scanner = new InterfaceScanner(environment);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CapabilityClient.class));

        ClassLoader classLoader = classLoader();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                register(registry, candidate.getBeanClassName(), classLoader);
            }
        }
    }

    private void register(BeanDefinitionRegistry registry, String className, ClassLoader classLoader) {
        if (className == null) {
            return;
        }
        Class<?> interfaceType;
        try {
            interfaceType = ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            return;
        }
        String beanName = interfaceType.getName();
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(CapabilityClientFactoryBean.class);
        definition.getPropertyValues().add("type", interfaceType);
        definition.setLazyInit(false);
        registry.registerBeanDefinition(beanName, definition);
    }

    private ClassLoader classLoader() {
        if (beanFactory instanceof ConfigurableListableBeanFactory clbf && clbf.getBeanClassLoader() != null) {
            return clbf.getBeanClassLoader();
        }
        return ClassUtils.getDefaultClassLoader();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No-op: all work happens during bean-definition registration.
    }

    /** A scanner that accepts standalone interfaces (which the default provider rejects). */
    private static final class InterfaceScanner extends ClassPathScanningCandidateComponentProvider {

        InterfaceScanner(Environment environment) {
            super(false, environment);
        }

        @Override
        protected boolean isCandidateComponent(
                org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
        }
    }
}
