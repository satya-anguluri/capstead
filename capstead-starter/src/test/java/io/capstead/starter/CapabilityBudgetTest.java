package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;
import io.capstead.core.CapabilityBudgetException;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityBudgetTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withPropertyValues(
                    "capstead.cost.models.claude-sonnet.input-per-million-tokens=3.00",
                    "capstead.cost.models.claude-sonnet.output-per-million-tokens=15.00")
            .withUserConfiguration(SampleCapabilities.class);

    @Test
    void blocksInvocationOnceDailyBudgetIsReached() {
        runner.run(context -> {
            SampleService service = context.getBean(SampleService.class);

            // First call: $0 spent < $0.04 budget → proceeds and records ~$0.04842.
            assertThat(service.generateLesson("Graphs")).isEqualTo("lesson:Graphs");

            // Second call: spend now exceeds the budget → blocked.
            assertThatThrownBy(() -> service.generateLesson("Trees"))
                    .isInstanceOf(CapabilityBudgetException.class);

            InMemoryCapabilityExecutionStore store =
                    context.getBean(InMemoryCapabilityExecutionStore.class);
            assertThat(store.scorecards().get(0).invocations()).isEqualTo(1);
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
        @Capability(name = "Generate Lesson", domain = "EngineerPrep", owner = "Content Team", version = "1")
        @DailyBudget("$0.04")
        public String generateLesson(String topic) {
            CapabilityExecutionContext.recordModel("claude-sonnet");
            CapabilityExecutionContext.recordTokens(1240, 2980);
            return "lesson:" + topic;
        }
    }
}
