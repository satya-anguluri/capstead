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
 * Verifies convention-based (config scan) capabilities register and are intercepted with no source
 * changes, and that they coexist with — and defer to — {@code @Capability} annotations.
 */
class CapabilityScanTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class))
            .withUserConfiguration(Fixtures.class)
            .withPropertyValues(
                    "capstead.scan[0].base-package=io.capstead.starter",
                    "capstead.scan[0].methods=generate*",
                    "capstead.scan[0].domain=Academy",
                    "capstead.scan[0].owner=Content Team",
                    "capstead.scan[0].tags=contentgen");

    @Test
    void registersScannedMethodAsCapabilityWithoutAnnotation() {
        runner.run(context -> {
            CapabilityCatalog catalog = context.getBean(CapabilityCatalog.class);

            assertThat(catalog.capabilities()).anySatisfy(metadata -> {
                assertThat(metadata.name()).isEqualTo("Generate Report");
                assertThat(metadata.domain()).isEqualTo("Academy");
                assertThat(metadata.owner()).isEqualTo("Content Team");
                assertThat(metadata.tags()).containsExactly("contentgen");
            });
            // A method that doesn't match the glob is not a capability.
            assertThat(catalog.capabilities()).noneMatch(metadata -> metadata.name().equals("Build Stuff"));
        });
    }

    @Test
    void interceptsScannedMethod() {
        runner.run(context -> {
            ScannedService service = context.getBean(ScannedService.class);
            service.generateReport("Q3");
            service.generateReport("Q4");

            InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
            CapabilityScorecard card = store.scorecards().stream()
                    .filter(c -> c.name().equals("Generate Report"))
                    .findFirst()
                    .orElseThrow();

            assertThat(card.invocations()).isEqualTo(2);
            assertThat(card.successRate()).isEqualTo(1.0);
        });
    }

    @Test
    void annotationTakesPrecedenceOverScanRule() {
        runner.run(context -> {
            AnnotatedService service = context.getBean(AnnotatedService.class);
            service.generateThing(); // matches "generate*" AND carries @Capability("Special Thing")

            InMemoryCapabilityExecutionStore store = context.getBean(InMemoryCapabilityExecutionStore.class);
            assertThat(store.scorecards()).anyMatch(c -> c.name().equals("Special Thing"));
            assertThat(store.scorecards()).noneMatch(c -> c.name().equals("Generate Thing"));

            CapabilityCatalog catalog = context.getBean(CapabilityCatalog.class);
            assertThat(catalog.capabilities()).anyMatch(m -> m.name().equals("Special Thing"));
            assertThat(catalog.capabilities()).noneMatch(m -> m.name().equals("Generate Thing"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        ScannedService scannedService() {
            return new ScannedService();
        }

        @Bean
        AnnotatedService annotatedService() {
            return new AnnotatedService();
        }
    }

    static class ScannedService {
        public String generateReport(String period) {
            return "report:" + period;
        }

        public String buildStuff() {
            return "stuff";
        }
    }

    static class AnnotatedService {
        @Capability(name = "Special Thing", domain = "Academy", owner = "Content Team", version = "1")
        public String generateThing() {
            return "thing";
        }
    }
}
