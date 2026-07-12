package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.runtime.AnnotationCapabilityMetadataResolver;
import io.capstead.runtime.CapabilityBudgetLedger;
import io.capstead.runtime.CapabilityCatalog;
import io.capstead.runtime.CapabilityDataRedactor;
import io.capstead.runtime.CapabilityDiscovery;
import io.capstead.runtime.CapabilityExecutionOptions;
import io.capstead.runtime.CapabilityExecutionPublisher;
import io.capstead.runtime.CapabilityExecutionRecorder;
import io.capstead.runtime.CapabilityMetadataResolver;
import io.capstead.runtime.CapabilityMethodInterceptor;
import io.capstead.runtime.CapabilityNamingStrategy;
import io.capstead.runtime.CapabilityPrincipalProvider;
import io.capstead.runtime.CapabilityRegistry;
import io.capstead.runtime.CapabilityScanRule;
import io.capstead.runtime.CapabilitySignatureValidator;
import io.capstead.runtime.CompositeCapabilityMetadataResolver;
import io.capstead.runtime.ConfiguredCapabilityRegistrar;
import io.capstead.runtime.DefaultCapabilityNamingStrategy;
import io.capstead.runtime.ExplicitCapabilityMetadataResolver;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;
import io.capstead.runtime.NoOpCapabilityDataRedactor;
import io.capstead.runtime.PricingTokenCostEstimator;
import io.capstead.runtime.ScanRuleCapabilityMetadataResolver;
import io.capstead.runtime.TokenCostEstimator;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Auto-configures Capstead in any Spring Boot application on the classpath.
 *
 * <p>Adding the starter is enough: the registry, discovery, catalog, execution store, cost estimator
 * and the AOP interceptor that captures a {@code CapabilityExecution} per invocation are all wired
 * automatically. Actuator endpoints are exposed when Actuator is present. Every bean is
 * {@link ConditionalOnMissingBean}, so applications can override any piece.
 */
@AutoConfiguration
@EnableConfigurationProperties({CapsteadCostProperties.class, CapsteadScanProperties.class, CapsteadCapabilitiesProperties.class, CapsteadExecutionsProperties.class})
@Import(CapsteadAutoConfiguration.CapabilityAutoProxyRegistrar.class)
public class CapsteadAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapabilityRegistry capabilityRegistry() {
        return new CapabilityRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilitySignatureValidator capabilitySignatureValidator() {
        return new CapabilitySignatureValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityDiscovery capabilityDiscovery(ApplicationContext context,
                                                   CapabilityRegistry registry,
                                                   CapabilitySignatureValidator validator) {
        return new CapabilityDiscovery(context, registry, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityCatalog capabilityCatalog(CapabilityRegistry registry) {
        return new CapabilityCatalog(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemoryCapabilityExecutionStore capabilityExecutionStore(CapsteadExecutionsProperties executionsProperties) {
        return new InMemoryCapabilityExecutionStore(executionsProperties.getMaxHistory());
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityDataRedactor capabilityDataRedactor() {
        return new NoOpCapabilityDataRedactor();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityExecutionOptions capabilityExecutionOptions(CapsteadExecutionsProperties executionsProperties,
                                                                 CapabilityDataRedactor redactor,
                                                                 ObjectProvider<CapabilityPrincipalProvider> principalProvider) {
        return new CapabilityExecutionOptions(
                executionsProperties.isCaptureInput(),
                executionsProperties.isCaptureOutput(),
                executionsProperties.getCaptureMaxLength(),
                redactor,
                principalProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityExecutionPublisher capabilityExecutionPublisher(ObjectProvider<CapabilityExecutionRecorder> recorders,
                                                                     CapsteadExecutionsProperties executionsProperties) {
        CapabilityExecutionPublisher.Mode mode = executionsProperties.getRecordingMode();
        Executor executor = mode == CapabilityExecutionPublisher.Mode.ASYNC ? newRecordingExecutor() : null;
        return new CapabilityExecutionPublisher(
                recorders.orderedStream().collect(Collectors.toList()), mode, executor);
    }

    private static Executor newRecordingExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "capstead-execution-recorder");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenCostEstimator tokenCostEstimator(CapsteadCostProperties costProperties) {
        return new PricingTokenCostEstimator(costProperties.toRates());
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityBudgetLedger capabilityBudgetLedger() {
        return new CapabilityBudgetLedger();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityNamingStrategy capabilityNamingStrategy() {
        return new DefaultCapabilityNamingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExplicitCapabilityMetadataResolver explicitCapabilityMetadataResolver(ApplicationContext context,
                                                                                 CapabilityRegistry registry,
                                                                                 CapsteadCapabilitiesProperties capabilitiesProperties) {
        return new ExplicitCapabilityMetadataResolver(context, registry, capabilitiesProperties.toDeclarations());
    }

    @Bean
    @ConditionalOnMissingBean
    public CompositeCapabilityMetadataResolver capabilityMetadataResolver(ExplicitCapabilityMetadataResolver explicitResolver,
                                                                CapsteadScanProperties scanProperties,
                                                                CapabilityNamingStrategy namingStrategy) {
        return new CompositeCapabilityMetadataResolver(List.of(
                new AnnotationCapabilityMetadataResolver(),
                explicitResolver,
                new ScanRuleCapabilityMetadataResolver(scanProperties.toRules(), namingStrategy)));
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfiguredCapabilityRegistrar configuredCapabilityRegistrar(ApplicationContext context,
                                                                       CapabilityRegistry registry,
                                                                       CapsteadScanProperties scanProperties,
                                                                       CapabilityNamingStrategy namingStrategy) {
        return new ConfiguredCapabilityRegistrar(context, registry, scanProperties.toRules(), namingStrategy);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static CapabilityMethodInterceptor capabilityMethodInterceptor(CompositeCapabilityMetadataResolver metadataResolver,
                                                                          ObjectProvider<CapabilityExecutionPublisher> publisher,
                                                                          ObjectProvider<TokenCostEstimator> costEstimator,
                                                                          ObjectProvider<CapabilityBudgetLedger> budgetLedger,
                                                                          ObjectProvider<CapabilityExecutionOptions> options) {
        return new CapabilityMethodInterceptor(
                metadataResolver,
                publisher::getObject,
                costEstimator::getIfAvailable,
                budgetLedger::getIfAvailable,
                options::getIfAvailable);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor capabilityExecutionAdvisor(CapabilityMethodInterceptor interceptor,
                                                     ExplicitCapabilityMetadataResolver explicitResolver,
                                                     CapsteadScanProperties scanProperties) {
        Pointcut pointcut = new AnnotationMatchingPointcut(null, Capability.class, true);
        if (!explicitResolver.methods().isEmpty()) {
            pointcut = Pointcuts.union(pointcut,
                    new ExplicitCapabilityMethodMatcherPointcut(explicitResolver.methods()));
        }
        List<CapabilityScanRule> rules = scanProperties.toRules();
        if (!rules.isEmpty()) {
            pointcut = Pointcuts.union(pointcut, new ScanRuleMethodMatcherPointcut(rules));
        }
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }

    /**
     * Wires the actuator endpoints only when Actuator is on the classpath, so applications without
     * Actuator still get the registry, catalog and execution store without a hard dependency.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    static class CapabilitiesEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CapabilitiesEndpoint capabilitiesEndpoint(CapabilityCatalog catalog) {
            return new CapabilitiesEndpoint(catalog);
        }

        @Bean
        @ConditionalOnMissingBean
        public CapabilityScorecardEndpoint capabilityScorecardEndpoint(InMemoryCapabilityExecutionStore store) {
            return new CapabilityScorecardEndpoint(store);
        }

        @Bean
        @ConditionalOnMissingBean
        public CapabilityExecutionsEndpoint capabilityExecutionsEndpoint(InMemoryCapabilityExecutionStore store) {
            return new CapabilityExecutionsEndpoint(store);
        }
    }

    /**
     * Serves the Capstead dashboard at {@code /capstead} and {@code /capstead/} (forwarding to the
     * bundled {@code /capstead/index.html}) when running as a servlet web app with Spring MVC.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class DashboardConfiguration implements WebMvcConfigurer {
        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            registry.addViewController("/capstead").setViewName("forward:/capstead/index.html");
            registry.addViewController("/capstead/").setViewName("forward:/capstead/index.html");
        }
    }

    /**
     * Registers the canonical Spring infrastructure auto-proxy creator (no AspectJ needed) and
     * forces class-based proxies so concrete capability beans are proxied.
     */
    static class CapabilityAutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
        }
    }
}
