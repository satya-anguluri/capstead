package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.CapabilityExecutionRecorder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies executions carry ids, nested capabilities link to their parent (automatic tree), every
 * model invocation is captured, and the legacy single-invocation enrichment still works.
 */
class CapabilityExecutionTreeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withUserConfiguration(Fixtures.class);

    @Test
    void linksNestedExecutionToItsParent() {
        runner.run(context -> {
            context.getBean(CourseService.class).generateCourse("Trees");
            CapturingRecorder recorder = context.getBean(CapturingRecorder.class);

            CapabilityExecution course = recorder.byName("Generate Course");
            CapabilityExecution lesson = recorder.byName("Generate Lesson");

            assertThat(course.executionId()).isNotBlank();
            assertThat(course.parentExecutionId()).isNull();
            assertThat(lesson.parentExecutionId()).isEqualTo(course.executionId());
        });
    }

    @Test
    void capturesEveryModelInvocation() {
        runner.run(context -> {
            context.getBean(MultiCallService.class).run();
            CapabilityExecution execution = context.getBean(CapturingRecorder.class).byName("Multi Call");

            assertThat(execution.modelInvocations()).hasSize(2);
            assertThat(execution.inputTokens()).isEqualTo(300);   // 100 + 200
            assertThat(execution.outputTokens()).isEqualTo(75);   // 25 + 50
            assertThat(execution.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.03"));
        });
    }

    @Test
    void legacySingleInvocationEnrichmentStillWorks() {
        runner.run(context -> {
            context.getBean(LegacyService.class).run();
            CapabilityExecution execution = context.getBean(CapturingRecorder.class).byName("Legacy");

            assertThat(execution.modelInvocations()).hasSize(1);
            assertThat(execution.inputTokens()).isEqualTo(1240);
            assertThat(execution.outputTokens()).isEqualTo(2980);
            assertThat(execution.model()).isEqualTo("claude-sonnet");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        CapturingRecorder capturingRecorder() {
            return new CapturingRecorder();
        }

        @Bean
        LessonService lessonService() {
            return new LessonService();
        }

        @Bean
        CourseService courseService(LessonService lessonService) {
            return new CourseService(lessonService);
        }

        @Bean
        MultiCallService multiCallService() {
            return new MultiCallService();
        }

        @Bean
        LegacyService legacyService() {
            return new LegacyService();
        }
    }

    static class CapturingRecorder implements CapabilityExecutionRecorder {

        private final List<CapabilityExecution> executions = new ArrayList<>();

        @Override
        public synchronized void record(CapabilityExecution execution) {
            executions.add(execution);
        }

        synchronized CapabilityExecution byName(String name) {
            return executions.stream()
                    .filter(e -> e.capabilityName().equals(name))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("no execution recorded for '" + name + "'"));
        }
    }

    static class LessonService {
        @Capability(name = "Generate Lesson", version = "1")
        public String generateLesson(String topic) {
            return "lesson:" + topic;
        }
    }

    static class CourseService {
        private final LessonService lessons;

        CourseService(LessonService lessons) {
            this.lessons = lessons;
        }

        @Capability(name = "Generate Course", version = "1")
        public String generateCourse(String topic) {
            return "course:" + lessons.generateLesson(topic);
        }
    }

    static class MultiCallService {
        @Capability(name = "Multi Call", version = "1")
        public void run() {
            CapabilityExecutionContext.recordModelInvocation("claude", 100, 25, new BigDecimal("0.01"));
            CapabilityExecutionContext.recordModelInvocation("nova", 200, 50, new BigDecimal("0.02"));
        }
    }

    static class LegacyService {
        @Capability(name = "Legacy", version = "1")
        public void run() {
            CapabilityExecutionContext.recordModel("claude-sonnet");
            CapabilityExecutionContext.recordTokens(1240, 2980);
        }
    }
}
