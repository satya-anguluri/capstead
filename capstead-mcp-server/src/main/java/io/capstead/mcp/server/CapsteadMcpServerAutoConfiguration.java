package io.capstead.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.capstead.mcp.CapabilityToolCatalog;
import io.capstead.mcp.CapabilityToolInvoker;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the Spring AI MCP server bridge on top of {@code capstead-mcp}.
 *
 * <p>When Spring AI's tool API is present and the {@link CapabilityToolCatalog} exists, this
 * registers a {@link ToolCallbackProvider} that exposes every governed capability as an MCP tool.
 * Add an MCP server Boot starter to the application and the tools are served over the chosen
 * transport automatically.
 */
@AutoConfiguration(afterName = {
        "io.capstead.starter.CapsteadAutoConfiguration",
        "io.capstead.mcp.CapsteadMcpAutoConfiguration"})
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnBean(CapabilityToolCatalog.class)
public class CapsteadMcpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "capabilityToolCallbackProvider")
    public ToolCallbackProvider capabilityToolCallbackProvider(CapabilityToolCatalog catalog,
                                                               CapabilityToolInvoker invoker,
                                                               ObjectProvider<ObjectMapper> objectMapper) {
        return new CapabilityToolCallbackProvider(
                catalog, invoker, objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
