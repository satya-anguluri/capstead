package io.capstead.platform;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.platform.expectation.ToolIdentityResolver;
import io.capstead.core.CapabilityMetadata;
import io.capstead.core.ModelInvocation;
import io.capstead.mcp.CapabilityToolCatalog;
import io.capstead.mcp.CapabilityToolMapper;
import io.capstead.runtime.CapabilityDescriptor;
import io.capstead.runtime.CapabilityExecutionQuery;
import io.capstead.runtime.CapabilityRegistry;
import io.capstead.platform.compare.NativeExecutionAdapter;
import io.capstead.platform.completeness.CompletenessSignal;
import io.capstead.platform.completeness.NativeCompletenessPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test fixture helper: assembles a minimal real 0.9.0 stack (registry, catalog, in-memory store)
 * and provides builders for executions, so tests exercise ACTUAL 0.9.0 classes rather than mocks.
 */
public final class TestStack {

    public final CapabilityRegistry registry = new CapabilityRegistry();
    public final CapabilityToolMapper mapper = new CapabilityToolMapper();
    public final CapabilityToolCatalog catalog = new CapabilityToolCatalog(registry, mapper);
    public final ToolIdentityResolver identityResolver = new ToolIdentityResolver(catalog);
    public final InMemoryQuery query = new InMemoryQuery();
    public final NativeCompletenessPolicy completeness = new NativeCompletenessPolicy(
            NativeCompletenessPolicy.RecordingMode.IN_PROCESS);

    public TestStack register(String name, String version) {
        registry.register(new CapabilityDescriptor(
                new CapabilityMetadata(name, "test capability", "test", "test-owner", version, List.of()),
                new Object(), null, new String[0]));
        return this;
    }

    /** Canonical tool name the mapper produces for a capability. */
    public String toolName(String name, String version) {
        return mapper.toolName(new CapabilityMetadata(name, null, null, null, version, null));
    }

    /** Minimal in-memory CapabilityExecutionQuery over test executions. */
    public static final class InMemoryQuery implements CapabilityExecutionQuery {
        private final List<CapabilityExecution> executions = new ArrayList<>();

        public void add(CapabilityExecution e) {
            executions.add(e);
        }

        @Override
        public List<CapabilityScorecard> scorecards() {
            return List.of();
        }

        @Override
        public List<CapabilityExecution> recent() {
            return List.copyOf(executions);
        }

        @Override
        public List<CapabilityExecution> recentFor(String name) {
            return executions.stream().filter(e -> e.capabilityName().equals(name)).toList();
        }

        @Override
        public Optional<CapabilityExecution> byId(String executionId) {
            return executions.stream().filter(e -> e.executionId().equals(executionId)).findFirst();
        }

        @Override
        public List<CapabilityExecution> childrenOf(String executionId) {
            return executions.stream()
                    .filter(e -> executionId.equals(e.parentExecutionId()))
                    .toList();
        }

        @Override
        public List<CapabilityExecution> subtree(String executionId) {
            // Mirror 0.9.0's contract: root first, then descendants, parents before children.
            List<CapabilityExecution> out = new ArrayList<>();
            byId(executionId).ifPresent(out::add);
            int i = 0;
            while (i < out.size()) {
                out.addAll(childrenOf(out.get(i).executionId()));
                i++;
            }
            return out;
        }
    }

    /** Builder shim for test executions (0.9.0's real record). */
    public static CapabilityExecution execution(String id, String parent, String capability,
                                                String version, boolean success, String errorType,
                                                Map<String, String> attributes,
                                                ModelInvocation... invocations) {
        var b = CapabilityExecution.builder(capability, version)
                .startedAt(Instant.now())
                .durationMs(10)
                .success(success)
                .errorType(errorType);
        if (parent != null) {
            b.parentExecutionId(parent);
        }
        attributes.forEach(b::attribute);
        for (ModelInvocation mi : invocations) {
            b.addModelInvocation(mi);
        }
        return b.executionId(id).finishedAt(Instant.now()).build();
    }

    public static ModelInvocation invocation(String model) {
        return new ModelInvocation(model, 10, 20, new BigDecimal("0.001"), Instant.now());
    }
}