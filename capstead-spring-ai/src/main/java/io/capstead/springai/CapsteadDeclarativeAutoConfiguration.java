package io.capstead.springai;

import io.capstead.runtime.CapabilityRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Enables declarative capabilities ({@link io.capstead.annotation.CapabilityClient}) when Spring AI's
 * {@code ChatClient} is on the classpath.
 *
 * <p>Registers the interface scanner (which creates a proxy bean per client interface) and a registrar
 * that publishes those capabilities into the catalog when the Capstead runtime registry is present
 * (resolved lazily, so autoconfiguration ordering is irrelevant). Recording, cost and the execution
 * tree come from the standard {@code @Capability} advisor, so declarative capabilities are governed
 * identically to hand-written ones.
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(ModelProfileProperties.class)
public class CapsteadDeclarativeAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static CapabilityClientRegistrar capabilityClientRegistrar() {
        return new CapabilityClientRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CapabilityRegistry.class)
    public DeclarativeCapabilityCatalogRegistrar declarativeCapabilityCatalogRegistrar(
            ApplicationContext applicationContext) {
        return new DeclarativeCapabilityCatalogRegistrar(applicationContext);
    }
}
