package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walking a whole execution tree in one call.
 *
 * <p>{@code childrenOf} answers one level, which meant every caller wrote the traversal — and a caller who
 * forgot that a child can have children of its own got a truncated tree that looked complete. These cover
 * the traversal itself: what comes back, in what order, and what happens to the shapes a store cannot refuse
 * to hold.
 */
class InMemoryExecutionSubtreeTest {

    private final InMemoryCapabilityExecutionStore store = new InMemoryCapabilityExecutionStore();

    /** One execution, recorded. Parent is null for a root. */
    private void record(String id, String parent) {
        Instant now = Instant.now();
        store.record(CapabilityExecution.builder("cap-" + id, "1")
                .executionId(id)
                .parentExecutionId(parent)
                .startedAt(now)
                .finishedAt(now.plusMillis(1))
                .durationMs(1)
                .success(true)
                .build());
    }

    private List<String> ids(List<CapabilityExecution> executions) {
        return executions.stream().map(CapabilityExecution::executionId).toList();
    }

    /**
     * Three levels, because two would pass against an implementation that only fetched direct children and
     * called it a tree — which is exactly the behaviour being replaced.
     */
    @Test
    void returnsEveryDescendant_notJustDirectChildren() {
        record("root", null);
        record("a", "root");
        record("b", "root");
        record("a1", "a");
        record("a1x", "a1");
        record("elsewhere", null);
        record("elsewhere-child", "elsewhere");

        List<String> walked = ids(store.subtree("root"));

        assertThat(walked).containsExactlyInAnyOrder("root", "a", "b", "a1", "a1x");
        // And nothing from the other tree, which a query keyed on the wrong column would happily include.
        assertThat(walked).doesNotContain("elsewhere", "elsewhere-child");
    }

    /**
     * The ordering is a contract, not an accident: a consumer builds the nested shape in one pass over this
     * list, so a node arriving before its parent has nothing to attach to.
     */
    @Test
    void putsTheRootFirstAndEveryNodeAfterItsParent() {
        record("root", null);
        record("a", "root");
        record("a1", "a");
        record("a1x", "a1");

        List<String> walked = ids(store.subtree("root"));

        assertThat(walked).first().isEqualTo("root");
        for (CapabilityExecution execution : store.subtree("root")) {
            if (execution.parentExecutionId() != null) {
                assertThat(walked.indexOf(execution.parentExecutionId()))
                        .as("%s appears before its parent", execution.executionId())
                        .isLessThan(walked.indexOf(execution.executionId()));
            }
        }
    }

    @Test
    void aLeafIsItsOwnSubtree() {
        record("root", null);
        record("a", "root");

        assertThat(ids(store.subtree("a"))).containsExactly("a");
    }

    /**
     * Empty, not a one-element list containing a fabricated root. An unknown id is a question about nothing.
     */
    @Test
    void anUnknownIdHasNoSubtree() {
        record("root", null);

        assertThat(store.subtree("no-such-execution")).isEmpty();
        assertThat(store.subtree(null)).isEmpty();
    }

    /**
     * The one that would take the process down rather than fail a test.
     *
     * <p>Nothing validates that parent links form a tree — they come from whatever recorded them, and a
     * record whose ancestor claims it as a parent is producible by an interceptor bug or a hand-written row.
     * A traversal without a visited set walks that forever, on a read path serving an actuator endpoint. It
     * is a query; it should return the odd shape it was given rather than become the outage.
     *
     * <p>Asserted with a timeout so that a regression fails in seconds instead of hanging the build.
     */
    @Test
    void aCycleTerminatesInsteadOfLoopingForever() {
        record("root", "leaf");   // root's parent is its own descendant
        record("mid", "root");
        record("leaf", "mid");

        List<String> walked = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(5), () -> ids(store.subtree("root")));

        // Every node once, and the walk stops when it comes back around.
        assertThat(walked).containsExactly("root", "mid", "leaf");
    }
}
