package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;
import io.capstead.runtime.CapabilityCatalog;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies explicit {@code capstead.capabilities} declarations: exact names on generic method names,
 * overload disambiguation, fail-fast validation, and coexistence with {@code @Capability}.
 */
class CapabilityDeclarationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withUserConfiguration(Fixtures.class);

    @Test
    void bindsExactNameToGenericMethodAndIntercepts() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Generate Lesson",
                        "capstead.capabilities[0].bean=lessonService",
                        "capstead.capabilities[0].method=generate",
                        "capstead.capabilities[0].domain=EngineerPrep",
                        "capstead.capabilities[0].owner=Content Team")
                .run(context -> {
                    CapabilityCatalog catalog = context.getBean(CapabilityCatalog.class);
                    assertThat(catalog.capabilities()).anySatisfy(metadata -> {
                        assertThat(metadata.name()).isEqualTo("Generate Lesson");
                        assertThat(metadata.domain()).isEqualTo("EngineerPrep");
                        assertThat(metadata.owner()).isEqualTo("Content Team");
                    });

                    context.getBean(LessonService.class).generate("Trees");

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    assertThat(store.scorecards())
                            .anyMatch(c -> c.name().equals("Generate Lesson") && c.invocations() == 1);
                });
    }

    @Test
    void disambiguatesOverloadByParameterTypes() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Ask Tutor",
                        "capstead.capabilities[0].bean=tutorService",
                        "capstead.capabilities[0].method=ask",
                        "capstead.capabilities[0].parameter-types[0]=java.lang.String")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    TutorService tutor = context.getBean(TutorService.class);
                    tutor.ask("q");   // the declared String overload — a capability
                    tutor.ask(42);    // the int overload — NOT a capability

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    CapabilityScorecard card = store.scorecards().stream()
                            .filter(c -> c.name().equals("Ask Tutor"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(card.invocations()).isEqualTo(1);
                });
    }

    @Test
    void failsFastOnUnknownBean() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Broken",
                        "capstead.capabilities[0].bean=doesNotExist",
                        "capstead.capabilities[0].method=generate")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastOnAmbiguousOverload() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Ask Tutor",
                        "capstead.capabilities[0].bean=tutorService",
                        "capstead.capabilities[0].method=ask")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void coexistsWithAnnotation() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Generate Lesson",
                        "capstead.capabilities[0].bean=lessonService",
                        "capstead.capabilities[0].method=generate")
                .run(context -> {
                    context.getBean(LessonService.class).generate("A");
                    context.getBean(AnnotatedService.class).classify();

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    assertThat(store.scorecards()).anyMatch(c -> c.name().equals("Generate Lesson"));
                    assertThat(store.scorecards()).anyMatch(c -> c.name().equals("Classify Question"));
                });
    }

    @Test
    void declaredUsageRuleMetersAndPricesWithoutClientCode() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Synthesize Speech",
                        "capstead.capabilities[0].bean=lessonService",
                        "capstead.capabilities[0].method=generate",
                        "capstead.capabilities[0].usage.model=eleven-v2",
                        "capstead.capabilities[0].usage.unit=characters",
                        "capstead.capabilities[0].usage.input-from-arg=0",
                        "capstead.cost.models.eleven-v2.input-per-million-tokens=220")
                .run(context -> {
                    context.getBean(LessonService.class).generate("Trees"); // 5 characters

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    CapabilityExecution execution = store.recent().get(0);
                    assertThat(execution.modelInvocations()).hasSize(1);
                    ModelInvocation invocation = execution.modelInvocations().get(0);
                    assertThat(invocation.model()).isEqualTo("eleven-v2");
                    assertThat(invocation.inputTokens()).isEqualTo(5);
                    assertThat(invocation.outputTokens()).isZero();
                    // 220 * 5 / 1_000_000 — priced by the same estimator as token-billed models
                    assertThat(invocation.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.001100"));
                });
    }

    @Test
    void declaredUsageRuleYieldsToRealEnrichment() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Draft Answer",
                        "capstead.capabilities[0].bean=enrichedService",
                        "capstead.capabilities[0].method=draft",
                        "capstead.capabilities[0].usage.model=should-not-be-recorded",
                        "capstead.capabilities[0].usage.unit=characters")
                .run(context -> {
                    context.getBean(EnrichedService.class).draft("question");

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    CapabilityExecution execution = store.recent().get(0);
                    assertThat(execution.modelInvocations()).hasSize(1);
                    assertThat(execution.modelInvocations().get(0).model()).isEqualTo("claude-sonnet");
                    assertThat(execution.modelInvocations().get(0).inputTokens()).isEqualTo(100);
                });
    }

    @Test
    void requestsUnitRecordsOneUnitPerInvocation() {
        runner.withPropertyValues(
                        "capstead.capabilities[0].name=Moderate Image",
                        "capstead.capabilities[0].bean=lessonService",
                        "capstead.capabilities[0].method=generate",
                        "capstead.capabilities[0].usage.model=moderation-v1",
                        "capstead.capabilities[0].usage.unit=requests")
                .run(context -> {
                    context.getBean(LessonService.class).generate("anything");

                    InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
                    CapabilityExecution execution = store.recent().get(0);
                    assertThat(execution.modelInvocations()).hasSize(1);
                    assertThat(execution.modelInvocations().get(0).inputTokens()).isEqualTo(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        LessonService lessonService() {
            return new LessonService();
        }

        @Bean
        TutorService tutorService() {
            return new TutorService();
        }

        @Bean
        AnnotatedService annotatedService() {
            return new AnnotatedService();
        }

        @Bean
        EnrichedService enrichedService() {
            return new EnrichedService();
        }
    }

    static class LessonService {
        public String generate(String topic) {
            return "lesson:" + topic;
        }
    }

    static class TutorService {
        public String ask(String question) {
            return "a:" + question;
        }

        public String ask(int n) {
            return "n:" + n;
        }
    }

    static class AnnotatedService {
        @Capability(name = "Classify Question", domain = "Academy", owner = "Content Team", version = "1")
        public String classify() {
            return "c";
        }
    }

    /** Enriches its own usage from inside the call — a declared rule must NOT override it. */
    static class EnrichedService {
        public String draft(String question) {
            CapabilityExecutionContext.recordModelInvocation("claude-sonnet", 100, 50, null);
            return "draft:" + question;
        }
    }
}
