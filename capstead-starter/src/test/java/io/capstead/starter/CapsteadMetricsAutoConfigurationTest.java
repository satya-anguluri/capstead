package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityStats;
import io.capstead.runtime.CapabilityStatsReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapsteadMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(SimpleMeterRegistry.class)
            .withConfiguration(AutoConfigurations.of(
                    CapsteadAutoConfiguration.class, CapsteadMetricsAutoConfiguration.class))
            .withUserConfiguration(SampleCapabilities.class);

    @Test
    void timesCapabilityInvocationsAndExposesStats() {
        runner.run(context -> {
            SampleService service = context.getBean(SampleService.class);
            service.generateLesson("HashMap Resize");
            service.generateLesson("Binary Trees");

            CapabilityStatsReader reader = context.getBean(CapabilityStatsReader.class);
            List<CapabilityStats> stats = reader.stats();

            assertThat(stats).hasSize(1);
            assertThat(stats.get(0).name()).isEqualTo("Generate Lesson");
            assertThat(stats.get(0).invocations()).isEqualTo(2);
            assertThat(stats.get(0).successRate()).isEqualTo(1.0);

            CapabilityMetricsEndpoint endpoint = context.getBean(CapabilityMetricsEndpoint.class);
            assertThat(endpoint.metrics()).hasSize(1);
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
                owner = "Content Team", version = "1", tags = {"lesson"})
        public String generateLesson(String topic) {
            return "lesson:" + topic;
        }
    }
}
