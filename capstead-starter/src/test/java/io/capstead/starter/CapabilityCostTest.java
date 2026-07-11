package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityScorecard;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCostTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withPropertyValues(
                    "capstead.cost.models.claude-sonnet.input-per-million-tokens=3.00",
                    "capstead.cost.models.claude-sonnet.output-per-million-tokens=15.00")
            .withUserConfiguration(SampleCapabilities.class);

    @Test
    void estimatesCostFromConfiguredModelRates() {
        runner.run(context -> {
            context.getBean(SampleService.class).generateLesson("Graphs");

            InMemoryCapabilityExecutionStore store =
                    context.getBean(InMemoryCapabilityExecutionStore.class);
            CapabilityScorecard card = store.scorecards().get(0);

            // (3.00 * 1240 + 15.00 * 2980) / 1_000_000 = 0.048420
            assertThat(card.totalCost()).isEqualByComparingTo(new BigDecimal("0.048420"));
            assertThat(card.avgCost()).isEqualTo(0.04842);
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
                owner = "Content Team", version = "1")
        public String generateLesson(String topic) {
            CapabilityExecutionContext.recordModel("claude-sonnet");
            CapabilityExecutionContext.recordTokens(1240, 2980);
            return "lesson:" + topic;
        }
    }
}
