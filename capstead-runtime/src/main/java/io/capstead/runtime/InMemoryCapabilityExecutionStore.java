package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory sink for {@link CapabilityExecution}s: keeps a bounded history and rolling per-capability
 * aggregates.
 *
 * <p>Backs Capstead's own discovery surfaces — the scorecard endpoint and capability history — with
 * zero external infrastructure. It is the default {@link CapabilityExecutionRecorder}; production
 * deployments can add further recorders (database, event stream) alongside it.
 */
public class InMemoryCapabilityExecutionStore implements CapabilityExecutionRecorder, CapabilityExecutionQuery {

    private static final int DEFAULT_MAX_HISTORY = 200;

    private final int maxHistory;
    private final Deque<CapabilityExecution> recent = new ArrayDeque<>();
    private final Map<String, Aggregate> aggregates = new LinkedHashMap<>();

    public InMemoryCapabilityExecutionStore() {
        this(DEFAULT_MAX_HISTORY);
    }

    public InMemoryCapabilityExecutionStore(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    @Override
    public synchronized void record(CapabilityExecution execution) {
        recent.addFirst(execution);
        while (recent.size() > maxHistory) {
            recent.removeLast();
        }
        aggregates
                .computeIfAbsent(execution.coordinates(),
                        key -> new Aggregate(execution.capabilityName(), execution.version()))
                .add(execution);
    }

    /** Most-recent-first execution history (bounded). */
    public synchronized List<CapabilityExecution> recent() {
        return new ArrayList<>(recent);
    }

    /** Most-recent-first history for a single capability (all versions). */
    public synchronized List<CapabilityExecution> recentFor(String name) {
        return recent.stream()
                .filter(execution -> execution.capabilityName().equals(name))
                .collect(Collectors.toList());
    }

    /** The retained execution with the given id, if still in history. */
    public synchronized Optional<CapabilityExecution> byId(String executionId) {
        return recent.stream()
                .filter(execution -> executionId.equals(execution.executionId()))
                .findFirst();
    }

    /** Most-recent-first direct children of the given execution (its nested capability calls). */
    public synchronized List<CapabilityExecution> childrenOf(String executionId) {
        return recent.stream()
                .filter(execution -> executionId.equals(execution.parentExecutionId()))
                .collect(Collectors.toList());
    }

    /** Scorecards for every capability version seen, in first-seen order. */
    public synchronized List<CapabilityScorecard> scorecards() {
        return aggregates.values().stream()
                .map(Aggregate::toScorecard)
                .collect(Collectors.toList());
    }

    private static final class Aggregate {
        private final String name;
        private final String version;
        private long count;
        private long successes;
        private double totalLatencyMs;
        private double totalInputTokens;
        private double totalOutputTokens;
        private BigDecimal totalCost = BigDecimal.ZERO;

        private Aggregate(String name, String version) {
            this.name = name;
            this.version = version;
        }

        private void add(CapabilityExecution execution) {
            count++;
            if (execution.success()) {
                successes++;
            }
            totalLatencyMs += execution.durationMs();
            totalInputTokens += execution.inputTokens();
            totalOutputTokens += execution.outputTokens();
            if (execution.estimatedCost() != null) {
                totalCost = totalCost.add(execution.estimatedCost());
            }
        }

        private CapabilityScorecard toScorecard() {
            double successRate = count == 0 ? 1.0 : (double) successes / count;
            double avgLatency = count == 0 ? 0.0 : totalLatencyMs / count;
            double avgInput = count == 0 ? 0.0 : totalInputTokens / count;
            double avgOutput = count == 0 ? 0.0 : totalOutputTokens / count;
            double avgCost = count == 0 ? 0.0 : totalCost.doubleValue() / count;
            return new CapabilityScorecard(
                    name, version, count, successRate, avgLatency, avgInput, avgOutput, avgCost, totalCost);
        }
    }
}
