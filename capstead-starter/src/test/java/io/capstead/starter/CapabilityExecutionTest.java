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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityExecutionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withUserConfiguration(SampleCapabilities.class);

    @Test
    void capturesExecutionsWithoutMicrometer() {
        runner.run(context -> {
            SampleService service = context.getBean(SampleService.class);
            service.generateLesson("HashMap Resize");
            service.generateLesson("Binary Trees");

            InMemoryCapabilityExecutionStore store =
                    context.getBean(InMemoryCapabilityExecutionStore.class);
            List<CapabilityScorecard> scorecards = store.scorecards();

            assertThat(scorecards).hasSize(1);
            CapabilityScorecard card = scorecards.get(0);
            assertThat(card.name()).isEqualTo("Generate Lesson");
            assertThat(card.invocations()).isEqualTo(2);
            assertThat(card.successRate()).isEqualTo(1.0);
            assertThat(store.recentFor("Generate Lesson")).hasSize(2);
        });
    }

    @Test
    void enrichesExecutionWithTokensAndCost() {
        runner.run(context -> {
            SampleService service = context.getBean(SampleService.class);
            service.generateLesson("Graphs"); // body enriches via CapabilityExecutionContext

            InMemoryCapabilityExecutionStore store =
                    context.getBean(InMemoryCapabilityExecutionStore.class);
            CapabilityScorecard card = store.scorecards().get(0);

            assertThat(card.avgInputTokens()).isEqualTo(1240.0);
            assertThat(card.avgOutputTokens()).isEqualTo(2980.0);
            assertThat(card.totalCost()).isEqualByComparingTo(new BigDecimal("0.023"));
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
            // Simulates what a Spring AI observation bridge would do after the model call.
            CapabilityExecutionContext.recordModel("claude-sonnet");
            CapabilityExecutionContext.recordTokens(1240, 2980);
            CapabilityExecutionContext.recordCost(new BigDecimal("0.023"));
            return "lesson:" + topic;
        }
    }
}
