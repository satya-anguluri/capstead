package io.capstead.starter;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityScorecard;
import io.capstead.runtime.CapabilityCatalog;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
