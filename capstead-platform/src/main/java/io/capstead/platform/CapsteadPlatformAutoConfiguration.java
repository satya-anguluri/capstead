package io.capstead.platform;

import io.capstead.mcp.CapabilityToolCatalog;
import io.capstead.platform.compare.ExpectedActualComparator;
import io.capstead.platform.compare.NativeExecutionAdapter;
import io.capstead.platform.completeness.NativeCompletenessPolicy;
import io.capstead.platform.expectation.ConfiguredExpectationSource;
import io.capstead.platform.expectation.ExpectationRegistry;
import io.capstead.platform.expectation.ToolIdentityResolver;
import io.capstead.platform.incident.InMemoryIncidentStore;
import io.capstead.platform.incident.IncidentStore;
import io.capstead.platform.ingest.EvaluationQueue;
import io.capstead.platform.ingest.NativeExecutionIngestRecorder;
import io.capstead.platform.rule.ExpectedToolNotObservedRule;
import io.capstead.platform.web.IncidentEndpoint;
import io.capstead.runtime.CapabilityExecutionQuery;
import io.capstead.runtime.CapabilityExecutionRecorder;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Wires the incident-intelligence slice from existing 0.9.0 beans. The platform registers one
 * additional {@code CapabilityExecutionRecorder} (0.9.0's publisher fans out to it — zero 0.9.0
 * changes), reads executions through {@code CapabilityExecutionQuery}, seeds expectations from
 * configuration validated against {@code CapabilityToolCatalog}, and exposes read-only incidents.
 */
@AutoConfiguration
@ConditionalOnBean(CapabilityExecutionQuery.class)
@EnableConfigurationProperties(CapsteadPlatformProperties.class)
public class CapsteadPlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolIdentityResolver toolIdentityResolver(CapabilityToolCatalog catalog) {
        return new ToolIdentityResolver(catalog);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExpectationRegistry expectationRegistry(ConfiguredExpectationSource source) {
        ExpectationRegistry registry = new ExpectationRegistry();
        source.expectations().forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfiguredExpectationSource configuredExpectationSource(
            CapsteadPlatformProperties properties, ToolIdentityResolver resolver) {
        List<ConfiguredExpectationSource.Declaration> declarations =
                properties.declarations().stream()
                        .map(d -> new ConfiguredExpectationSource.Declaration(
                                d.capability(), d.version(), d.expectedOperation(),
                                d.required(), d.condition()))
                        .toList();
        return new ConfiguredExpectationSource(declarations, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public NativeExecutionAdapter nativeExecutionAdapter(CapabilityExecutionQuery query,
            ToolIdentityResolver resolver) {
        return new NativeExecutionAdapter(query,
                (capabilityName, version) -> resolver
                        .resolve(capabilityName, version)
                        .map(ToolIdentityResolver.ToolIdentity::toolName)
                        .orElse(null));
    }

    @Bean
    @ConditionalOnMissingBean
    public ExpectedActualComparator expectedActualComparator(NativeExecutionAdapter adapter) {
        return new ExpectedActualComparator(adapter);
    }

    @Bean
    @ConditionalOnMissingBean
    public NativeCompletenessPolicy nativeCompletenessPolicy(
            CapsteadPlatformProperties properties) {
        return new NativeCompletenessPolicy(
                properties.crossInstanceRecording()
                        ? NativeCompletenessPolicy.RecordingMode.NOT_PROVABLE
                        : NativeCompletenessPolicy.RecordingMode.IN_PROCESS);
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentStore incidentStore() {
        return new InMemoryIncidentStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExpectedToolNotObservedRule expectedToolNotObservedRule() {
        return new ExpectedToolNotObservedRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public EvaluationQueue evaluationQueue() {
        return new EvaluationQueue();
    }

    /** The platform's ingestion: a 0.9.0 recorder that only queues root execution ids. */
    @Bean
    @ConditionalOnMissingBean
    public CapabilityExecutionRecorder nativeExecutionIngestRecorder(EvaluationQueue queue) {
        return new NativeExecutionIngestRecorder(queue);
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentEvaluator incidentEvaluator(CapabilityExecutionQuery query,
            NativeExecutionAdapter adapter, ExpectationRegistry registry,
            ExpectedActualComparator comparator, NativeCompletenessPolicy completenessPolicy,
            List<ExpectedToolNotObservedRule> rules, IncidentStore store) {
        return new IncidentEvaluator(query, adapter, registry, comparator, completenessPolicy,
                List.copyOf(rules), store);
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentEndpoint incidentEndpoint(IncidentStore store) {
        return new IncidentEndpoint(store);
    }
}