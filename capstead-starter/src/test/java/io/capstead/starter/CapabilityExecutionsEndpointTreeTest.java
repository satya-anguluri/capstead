package io.capstead.starter;

import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@code GET /actuator/capabilityexecutions/{id}} actually returns.
 *
 * <p>The response type has always been called {@code ExecutionTree} and was, until now, one execution plus
 * its direct children. A capability that called a capability that called another reported the middle layer
 * and stopped, so a reader saw a complete-looking answer that was missing everything below depth one.
 */
class CapabilityExecutionsEndpointTreeTest {

    private final InMemoryCapabilityExecutionStore store = new InMemoryCapabilityExecutionStore();
    private final CapabilityExecutionsEndpoint endpoint = new CapabilityExecutionsEndpoint(store);

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

    private static List<String> childIds(CapabilityExecutionsEndpoint.ExecutionTree node) {
        return node.children().stream().map(child -> child.execution().executionId()).toList();
    }

    /**
     * Three levels, because two pass against the flat shape this replaces.
     */
    @Test
    void nestsToFullDepth() {
        record("root", null);
        record("a", "root");
        record("b", "root");
        record("a1", "a");
        record("a1x", "a1");

        CapabilityExecutionsEndpoint.ExecutionTree tree = endpoint.execution("root");

        assertThat(tree.execution().executionId()).isEqualTo("root");
        assertThat(childIds(tree)).containsExactlyInAnyOrder("a", "b");

        CapabilityExecutionsEndpoint.ExecutionTree a = tree.children().stream()
                .filter(child -> "a".equals(child.execution().executionId())).findFirst().orElseThrow();
        assertThat(childIds(a)).containsExactly("a1");

        // The level the old shape could not reach at all.
        CapabilityExecutionsEndpoint.ExecutionTree a1 = a.children().get(0);
        assertThat(childIds(a1)).containsExactly("a1x");
        assertThat(a1.children().get(0).children()).isEmpty();
    }

    @Test
    void aLeafHasAnEmptyChildList_notNull() {
        record("root", null);

        CapabilityExecutionsEndpoint.ExecutionTree tree = endpoint.execution("root");

        assertThat(tree.execution().executionId()).isEqualTo("root");
        assertThat(tree.children()).isEmpty();
    }

    /**
     * Null, as before, so a caller that already handled "aged out of history" keeps working.
     */
    @Test
    void anUnknownIdIsStillNull() {
        assertThat(endpoint.execution("no-such-execution")).isNull();
    }

    /**
     * The endpoint must not become the place a cycle takes the process down. The store bounds the walk;
     * this asserts the nesting step does not reintroduce the problem by looping over the result.
     */
    @Test
    void aCycleDoesNotHangTheEndpoint() {
        record("root", "leaf");
        record("mid", "root");
        record("leaf", "mid");

        CapabilityExecutionsEndpoint.ExecutionTree tree = org.junit.jupiter.api.Assertions
                .assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> endpoint.execution("root"));

        assertThat(tree.execution().executionId()).isEqualTo("root");
        assertThat(childIds(tree)).containsExactly("mid");
    }
}
