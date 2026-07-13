package io.capstead.starter.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Enables declarative capabilities ({@link io.capstead.annotation.CapabilityClient}) for <em>any</em>
 * application — no Spring AI required.
 *
 * <p>Registers the interface scanner (a proxy bean per client interface), the structured-output binder,
 * and a catalog registrar. Each declarative call is dispatched to the application's
 * {@link io.capstead.runtime.CapabilityModelInvoker} bean; {@code capstead-spring-ai} provides a default
 * Spring AI implementation, or projects supply their own (LangChain4j, an SDK, a plain HTTP client).
 * Recording, cost and the execution tree come from the standard {@code @Capability} advisor.
 */
@AutoConfiguration
@EnableConfigurationProperties(ModelProfileProperties.class)
public class CapsteadDeclarativeAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static CapabilityClientRegistrar capabilityClientRegistrar() {
        return new CapabilityClientRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    public StructuredOutputBinder capabilityStructuredOutputBinder(ObjectProvider<ObjectMapper> objectMapper) {
        return new StructuredOutputBinder(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public DeclarativeCapabilityCatalogRegistrar declarativeCapabilityCatalogRegistrar(
            ApplicationContext applicationContext) {
        return new DeclarativeCapabilityCatalogRegistrar(applicationContext);
    }
}
