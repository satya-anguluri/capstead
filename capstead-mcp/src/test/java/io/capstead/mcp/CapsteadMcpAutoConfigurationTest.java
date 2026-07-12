package io.capstead.mcp;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that governed capabilities are exported as MCP tools and that invoking a tool routes
 * through the capability's governing proxy.
 */
class CapsteadMcpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.capstead.starter.CapsteadAutoConfiguration.class,
                    CapsteadMcpAutoConfiguration.class))
            .withUserConfiguration(Fixtures.class);

    @Test
    void exportsEachCapabilityAsAnMcpTool() {
        runner.run(context -> {
            CapabilityToolCatalog catalog = context.getBean(CapabilityToolCatalog.class);

            List<CapabilityTool> tools = catalog.tools();
            assertThat(tools).hasSize(1);

            CapabilityTool tool = tools.get(0);
            assertThat(tool.name()).isEqualTo("greet_v1");
            assertThat(tool.title()).isEqualTo("Greet");
            assertThat(tool.description()).isEqualTo("Greets a person by name");
            assertThat(tool.governance())
                    .containsEntry("coordinates", "Greet@1")
                    .containsEntry("owner", "Growth Team");

            @SuppressWarnings("unchecked")
            Map<String, Object> schema = tool.inputSchema();
            assertThat(schema).containsEntry("type", "object");
            assertThat(schema).containsEntry("required", List.of("name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties).containsKey("name");
        });
    }

    @Test
    void invokesToolThroughGovernedProxy() {
        runner.run(context -> {
            CapabilityToolInvoker invoker = context.getBean(CapabilityToolInvoker.class);

            CapabilityToolResult result = invoker.call("greet_v1", Map.of("name", "World"));

            assertThat(result.isError()).isFalse();
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).text()).isEqualTo("Hello, World!");
        });
    }

    @Test
    void unknownToolYieldsErrorResult() {
        runner.run(context -> {
            CapabilityToolInvoker invoker = context.getBean(CapabilityToolInvoker.class);

            CapabilityToolResult result = invoker.call("does_not_exist_v1", Map.of());

            assertThat(result.isError()).isTrue();
            assertThat(result.content().get(0).text()).contains("Unknown capability tool");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        Greeter greeter() {
            return new Greeter();
        }
    }

    static class Greeter {

        @Capability(name = "Greet",
                description = "Greets a person by name",
                domain = "Marketing",
                owner = "Growth Team",
                version = "1")
        @DailyBudget("$10")
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }
}
