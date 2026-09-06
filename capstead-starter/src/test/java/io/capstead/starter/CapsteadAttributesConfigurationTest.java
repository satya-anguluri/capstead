package io.capstead.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.ExecutionAttributeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The allow-list, as an application actually configures it.
 *
 * <p>The unit tests prove the registry enforces what it is given. These prove the thing that makes the
 * feature reachable at all: that {@code capstead.attributes.allowed} reaches
 * {@link CapabilityExecutionContext}, which is the static seam application code records through. Without
 * this wiring every call to {@code recordAttribute} is silently refused, and the feature is complete and
 * unusable.
 */
@DisplayName("capstead.attributes.allowed")
class CapsteadAttributesConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadAutoConfiguration.class));

    @AfterEach
    void reset() {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.useRegistry(ExecutionAttributeRegistry.empty());
    }

    /** Record one attribute against a live execution and report what survived. */
    private static String recordAndRead(String name, String value) {
        CapabilityExecutionContext.clear();
        CapabilityExecutionContext.begin(CapabilityExecution.builder("Answer Question", "1"));
        CapabilityExecutionContext.recordAttribute(name, value);
        return CapabilityExecutionContext.current().orElseThrow().attributes().get(name);
    }

    @Test
    @DisplayName("a declared name becomes recordable")
    void declaredNamesReachTheContext() {
        runner.withPropertyValues("capstead.attributes.allowed[0]=evidence.state").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExecutionAttributeRegistry.class).allows("evidence.state")).isTrue();

            assertThat(recordAndRead("evidence.state", "STALE_EVIDENCE"))
                    .as("the property must reach the static seam application code calls")
                    .isEqualTo("STALE_EVIDENCE");
        });
    }

    @Test
    @DisplayName("an application that declares nothing records nothing")
    void theDefaultAllowsNothing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExecutionAttributeRegistry.class).isEmpty()).isTrue();

            assertThat(recordAndRead("evidence.state", "STALE_EVIDENCE"))
                    .as("no declaration means no attributes, exactly as before the feature existed")
                    .isNull();
        });
    }

    @Test
    @DisplayName("a name that is not declared stays unrecordable even when others are")
    void anUndeclaredNameIsStillRefused() {
        runner.withPropertyValues("capstead.attributes.allowed[0]=evidence.state").run(context -> {
            assertThat(recordAndRead("policy.authorization.outcome", "DENY")).isNull();
        });
    }

    @Test
    @DisplayName("a malformed declared name fails closed rather than opening the gate")
    void malformedNamesAreDropped() {
        runner.withPropertyValues("capstead.attributes.allowed[0]=state").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExecutionAttributeRegistry.class).isEmpty())
                    .as("'state' has no namespace, so it is not a name and must not be allowed")
                    .isTrue();
            assertThat(recordAndRead("state", "ANYTHING")).isNull();
        });
    }

    @Test
    @DisplayName("several names can be declared, and each works")
    void severalNames() {
        runner.withPropertyValues(
                "capstead.attributes.allowed[0]=policy.authorization.outcome",
                "capstead.attributes.allowed[1]=evidence.sourceRevision",
                "capstead.attributes.allowed[2]=lab.fixture.version").run(context -> {
            ExecutionAttributeRegistry registry = context.getBean(ExecutionAttributeRegistry.class);
            assertThat(registry.allowed()).containsExactlyInAnyOrder(
                    "policy.authorization.outcome", "evidence.sourceRevision", "lab.fixture.version");

            assertThat(recordAndRead("evidence.sourceRevision", "17")).isEqualTo("17");
        });
    }
}
