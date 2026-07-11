package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.runtime.CapabilityBudgetLedger;
import io.capstead.runtime.CapabilityCatalog;
import io.capstead.runtime.CapabilityDiscovery;
import io.capstead.runtime.CapabilityExecutionRecorder;
import io.capstead.runtime.CapabilityMethodInterceptor;
import io.capstead.runtime.CapabilityRegistry;
import io.capstead.runtime.CapabilitySignatureValidator;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;
import io.capstead.runtime.PricingTokenCostEstimator;
import io.capstead.runtime.TokenCostEstimator;

import org.springframework.aop.Advisor;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;
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
@EnableConfigurationProperties(CapsteadCostProperties.class)
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
    public InMemoryCapabilityExecutionStore capabilityExecutionStore() {
        return new InMemoryCapabilityExecutionStore();
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
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static CapabilityMethodInterceptor capabilityMethodInterceptor(ObjectProvider<CapabilityExecutionRecorder> recorders,
                                                                          ObjectProvider<TokenCostEstimator> costEstimator,
                                                                          ObjectProvider<CapabilityBudgetLedger> budgetLedger) {
        return new CapabilityMethodInterceptor(
                () -> recorders.orderedStream().collect(Collectors.toList()),
                costEstimator::getIfAvailable,
                budgetLedger::getIfAvailable);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor capabilityExecutionAdvisor(CapabilityMethodInterceptor interceptor) {
        AnnotationMatchingPointcut pointcut =
                new AnnotationMatchingPointcut(null, Capability.class, true);
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
