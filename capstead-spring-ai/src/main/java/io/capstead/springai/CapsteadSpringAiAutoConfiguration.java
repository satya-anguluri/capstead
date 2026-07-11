package io.capstead.springai;

import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@link CapabilityAiObservationHandler} when Spring AI's chat model observations are
 * on the classpath.
 *
 * <p>Spring Boot automatically registers {@code ObservationHandler} beans with the observation
 * registry, so this handler starts enriching capability executions with token/model data as soon as
 * the bridge is on the classpath — no further configuration required.
 */
@AutoConfiguration
@ConditionalOnClass(ChatModelObservationContext.class)
public class CapsteadSpringAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapabilityAiObservationHandler capabilityAiObservationHandler() {
        return new CapabilityAiObservationHandler();
    }
}
