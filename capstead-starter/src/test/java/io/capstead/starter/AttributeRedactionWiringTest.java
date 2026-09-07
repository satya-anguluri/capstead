package io.capstead.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityDataRedactor;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redaction of attributes THROUGH A REAL CAPABILITY CALL.
 *
 * <p>The unit tests prove {@code sanitizeAttributes} rewrites what it is given. This proves the interceptor
 * actually calls it, which is the half that can silently not happen — and if it does not, an application's
 * configured redactor is bypassed for every attribute it records while appearing to work for captured I/O.
 */
@DisplayName("Attribute redaction, wired")
class AttributeRedactionWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withUserConfiguration(LeakyService.Config.class)
            .withPropertyValues("capstead.attributes.allowed[0]=policy.authorization.reason");

    @Test
    @DisplayName("an application's redactor reaches attribute values recorded inside a capability")
    void theRedactorIsApplied() {
        runner.run(context -> {
            context.getBean(LeakyService.class).decide();

            CapabilityExecution execution = context.getBean(InMemoryCapabilityExecutionStore.class)
                    .recentFor("Decide Access").get(0);

            assertThat(execution.attribute("policy.authorization.reason"))
                    .as("the interceptor must apply the configured redactor to attributes")
                    .isEqualTo("denied for token [redacted]");
        });
    }

    @Test
    @DisplayName("with no redactor bean the value is stored as recorded")
    void withoutARedactorNothingChanges() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
                .withUserConfiguration(LeakyService.PlainConfig.class)
                .withPropertyValues("capstead.attributes.allowed[0]=policy.authorization.reason")
                .run(context -> {
                    context.getBean(LeakyService.class).decide();

                    assertThat(context.getBean(InMemoryCapabilityExecutionStore.class)
                            .recentFor("Decide Access").get(0)
                            .attribute("policy.authorization.reason"))
                            .isEqualTo("denied for token sk-abc123");
                });
    }

    /** A capability that records a reason code which happens to carry a credential. */
    static class LeakyService {

        @Capability(name = "Decide Access", domain = "Policy")
        public void decide() {
            CapabilityExecutionContext.recordAttribute(
                    "policy.authorization.reason", "denied for token sk-abc123");
        }

        @Configuration
        static class Config {
            @Bean
            LeakyService leakyService() {
                return new LeakyService();
            }

            @Bean
            CapabilityDataRedactor redactor() {
                return value -> value == null ? null : value.replaceAll("sk-\\w+", "[redacted]");
            }
        }

        @Configuration
        static class PlainConfig {
            @Bean
            LeakyService leakyService() {
                return new LeakyService();
            }
        }
    }
}
