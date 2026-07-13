package io.capstead.springai;

import io.capstead.runtime.CapabilityModelInvoker;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a Spring AI {@code ChatClient}-backed {@link CapabilityModelInvoker} so declarative
 * capabilities work out of the box for Spring AI applications.
 *
 * <p>Only applies when {@code ChatClient} is on the classpath and a {@code ChatClient.Builder} bean
 * exists, and backs off if the application already defines its own {@code CapabilityModelInvoker}.
 * The declarative machinery itself lives in {@code capstead-starter} and needs no Spring AI.
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
public class CapsteadSpringAiInvokerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CapabilityModelInvoker.class)
    @ConditionalOnBean(ChatClient.Builder.class)
    public SpringAiCapabilityModelInvoker springAiCapabilityModelInvoker(ChatClient.Builder chatClientBuilder) {
        return new SpringAiCapabilityModelInvoker(chatClientBuilder);
    }
}
