package io.capstead.starter;

import io.capstead.runtime.CapabilityMetrics;
import io.capstead.runtime.CapabilityRegistry;
import io.capstead.runtime.CapabilityStatsReader;
import io.capstead.runtime.MicrometerCapabilityRecorder;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional Micrometer bridge: projects capability executions onto standard metrics tooling.
 *
 * <p>Active only when a {@link MeterRegistry} is present. It contributes a
 * {@link MicrometerCapabilityRecorder} (picked up automatically by the core interceptor), a
 * {@link CapabilityStatsReader}, and — when Actuator is present — the
 * {@code /actuator/capabilitymetrics} endpoint. Without a meter registry, execution capture and the
 * scorecard endpoint still work; only the Micrometer projection is skipped.
 */
@AutoConfiguration(after = CapsteadAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class CapsteadMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapabilityMetrics capabilityMetrics(MeterRegistry meterRegistry) {
        return new CapabilityMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public MicrometerCapabilityRecorder micrometerCapabilityRecorder(CapabilityMetrics metrics) {
        return new MicrometerCapabilityRecorder(metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityStatsReader capabilityStatsReader(CapabilityRegistry registry,
                                                       MeterRegistry meterRegistry) {
        return new CapabilityStatsReader(registry, meterRegistry);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    static class MetricsEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CapabilityMetricsEndpoint capabilityMetricsEndpoint(CapabilityStatsReader reader) {
            return new CapabilityMetricsEndpoint(reader);
        }
    }
}
