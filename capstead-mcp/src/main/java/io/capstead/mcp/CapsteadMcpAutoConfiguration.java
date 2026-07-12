package io.capstead.mcp;

import io.capstead.runtime.CapabilityRegistry;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configures Capstead's MCP export on top of the core starter.
 *
 * <p>Wires the tool catalog and invoker whenever a {@link CapabilityRegistry} is present (i.e. the
 * Capstead starter is on the classpath), and exposes the {@code capabilitymcp} actuator endpoint
 * when Actuator is available. Every bean is {@link ConditionalOnMissingBean} so applications can
 * override any piece.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.capstead.starter.CapsteadAutoConfiguration")
@ConditionalOnBean(CapabilityRegistry.class)
public class CapsteadMcpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapabilityToolMapper capabilityToolMapper() {
        return new CapabilityToolMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityToolCatalog capabilityToolCatalog(CapabilityRegistry registry, CapabilityToolMapper mapper) {
        return new CapabilityToolCatalog(registry, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityToolInvoker capabilityToolInvoker(CapabilityToolCatalog catalog) {
        return new CapabilityToolInvoker(catalog);
    }

    /**
     * Wires the MCP actuator endpoint only when Actuator is on the classpath, so applications
     * without Actuator still get the catalog and invoker beans without a hard dependency.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    static class McpEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CapabilityMcpEndpoint capabilityMcpEndpoint(CapabilityToolCatalog catalog,
                                                           CapabilityToolInvoker invoker) {
            return new CapabilityMcpEndpoint(catalog, invoker);
        }
    }
}
