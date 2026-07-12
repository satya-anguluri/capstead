package io.capstead.mcp.server;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;
import io.capstead.mcp.CapsteadMcpAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that governed capabilities are surfaced as Spring AI tools through a
 * {@link ToolCallbackProvider}, ready for an MCP server transport to serve.
 */
class CapsteadMcpServerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.capstead.starter.CapsteadAutoConfiguration.class,
                    CapsteadMcpAutoConfiguration.class,
                    CapsteadMcpServerAutoConfiguration.class))
            .withUserConfiguration(Fixtures.class);

    @Test
    void publishesCapabilitiesAsSpringAiToolCallbacks() {
        runner.run(context -> {
            ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);

            ToolCallback[] callbacks = provider.getToolCallbacks();
            assertThat(callbacks).hasSize(1);

            ToolCallback callback = callbacks[0];
            assertThat(callback.getToolDefinition().name()).isEqualTo("greet_v1");
            assertThat(callback.getToolDefinition().description()).isEqualTo("Greets a person by name");
            assertThat(callback.getToolDefinition().inputSchema()).contains("\"properties\"");
        });
    }

    @Test
    void invokingAToolRoutesThroughTheGovernedCapability() {
        runner.run(context -> {
            ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
            ToolCallback callback = provider.getToolCallbacks()[0];

            String result = callback.call("{\"name\":\"World\"}");

            assertThat(result).isEqualTo("Hello, World!");
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
