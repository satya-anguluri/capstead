package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.runtime.CapabilityCatalog;
import io.capstead.runtime.CapabilityRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CapsteadAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class));

    @Test
    void autoConfiguresCoreBeans() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(CapabilityRegistry.class)
                .hasSingleBean(CapabilityCatalog.class));
    }

    @Test
    void discoversAnnotatedCapabilitiesAndExposesThemViaEndpoint() {
        runner.withUserConfiguration(SampleCapabilities.class).run(context -> {
            CapabilityCatalog catalog = context.getBean(CapabilityCatalog.class);
            assertThat(catalog.capabilities()).hasSize(1);
            assertThat(catalog.capabilities().get(0).name()).isEqualTo("Generate Lesson");
            assertThat(catalog.capabilities().get(0).owner()).isEqualTo("Content Team");

            CapabilitiesEndpoint endpoint = context.getBean(CapabilitiesEndpoint.class);
            assertThat(endpoint.capabilities()).hasSize(1);
            assertThat(endpoint.byName("Generate Lesson")).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SampleCapabilities {
        @Bean
        SampleService sampleService() {
            return new SampleService();
        }
    }

    static class SampleService {
        @Capability(name = "Generate Lesson", domain = "EngineerPrep",
                owner = "Content Team", version = "1", tags = {"lesson", "java"})
        public String generateLesson(String topic) {
            return null; // real body would call Spring AI / LangChain4j
        }
    }
}
