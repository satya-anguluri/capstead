package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Sibling order within a subtree: most recent first, then by id so equal timestamps are still stable.
     *
     * <p>The id tie-break exists because executions recorded in the same millisecond are ordinary — a
     * capability that fans out to three tools does exactly that — and without it the order is whatever the
     * underlying store happened to produce, which the two implementations of {@code subtree} cannot agree on.
     */
    private static final java.util.Comparator<CapabilityExecution> SIBLING_ORDER =
            java.util.Comparator.comparing(CapabilityExecution::startedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                    .thenComparing(CapabilityExecution::executionId,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));

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

    /**
     * The given execution and every descendant beneath it, parents before children.
     *
     * <p>Breadth-first over one index built per call. Repeatedly filtering {@code recent} per level would be
     * O(levels x history) and this store holds only a bounded window, so one pass to group by parent is
     * both simpler and cheaper.
     *
     * <p><b>Visited ids are tracked, and that is not defensive padding.</b> Parent links come from whatever
     * recorded them, and this store never validates that the graph is acyclic. A cycle — a record whose
     * ancestor claims it as a parent, which an interceptor bug or a hand-written record can produce — would
     * otherwise loop until the process died, on a read path serving an actuator endpoint. It is a query: it
     * should return the odd shape it was given, not become the outage.
     */
    public synchronized List<CapabilityExecution> subtree(String executionId) {
        // Resolved once. byId streams the whole history, and calling it again for the same id inside a
        // synchronized method is a second full scan for an answer already in hand. Raised in review.
        Optional<CapabilityExecution> root = executionId == null ? Optional.empty() : byId(executionId);
        if (root.isEmpty()) {
            return List.of();
        }
        Map<String, List<CapabilityExecution>> byParent = new LinkedHashMap<>();
        for (CapabilityExecution execution : recent) {
            String parent = execution.parentExecutionId();
            if (parent != null) {
                byParent.computeIfAbsent(parent, key -> new ArrayList<>()).add(execution);
            }
        }
        // SIBLINGS MOST-RECENT-FIRST, tie-broken on id.
        //
        // Not cosmetic: childrenOf documents itself as most-recent-first and the JDBC reader orders that way
        // too, so a subtree that walked siblings the other way would contradict the query beside it and
        // disagree with the other implementation of this same method. Iteration order of `recent` happens to
        // give most-recent-first already, but relying on that leaves equal timestamps ordered by insertion —
        // which the JDBC reader cannot reproduce. Sorting explicitly is what makes the two agree. Raised in
        // review, where the JDBC side was ascending.
        byParent.values().forEach(children -> children.sort(SIBLING_ORDER));
        List<CapabilityExecution> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<CapabilityExecution> queue = new ArrayDeque<>();
        queue.add(root.orElseThrow());
        while (!queue.isEmpty()) {
            CapabilityExecution next = queue.removeFirst();
            if (!seen.add(next.executionId())) {
                continue;
            }
            ordered.add(next);
            queue.addAll(byParent.getOrDefault(next.executionId(), List.of()));
        }
        return ordered;
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
        private final Set<String> models = new LinkedHashSet<>();

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
            for (ModelInvocation invocation : execution.modelInvocations()) {
                if (invocation.model() != null) {
                    models.add(invocation.model());
                }
            }
        }

        private CapabilityScorecard toScorecard() {
            double successRate = count == 0 ? 1.0 : (double) successes / count;
            double avgLatency = count == 0 ? 0.0 : totalLatencyMs / count;
            double avgInput = count == 0 ? 0.0 : totalInputTokens / count;
            double avgOutput = count == 0 ? 0.0 : totalOutputTokens / count;
            double avgCost = count == 0 ? 0.0 : totalCost.doubleValue() / count;
            return new CapabilityScorecard(
                    name, version, count, successRate, avgLatency, avgInput, avgOutput, avgCost, totalCost,
                    new ArrayList<>(models));
        }
    }
}
