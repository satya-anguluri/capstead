package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips executions (with parent links and multiple model invocations) through the JDBC recorder
 * and reader against an in-memory H2 database using the Capstead-owned schema.
 */
class JdbcCapabilityExecutionRoundTripTest {

    private EmbeddedDatabase database;
    private JdbcCapabilityExecutionRecorder recorder;
    private JdbcCapabilityExecutionReader reader;

    /**
     * A DISTINCT database per test, and shut down afterwards.
     *
     * <p>Without {@code generateUniqueName}, every test in this class built a handle to the same H2 in-memory
     * database under its default name and none of them closed it, so rows written by one test were visible to
     * the next. It went unnoticed while each test used ids nobody else used; the subtree tests below reuse a
     * root id deliberately, and a leaked child from another test turned up inside the tree under assertion.
     *
     * <p>Left as it was, the failure mode is worse than a wrong assertion: tests pass alone and fail together,
     * or pass in one order and fail in another.
     */
    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("io/capstead/jdbc/capstead-schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        recorder = new JdbcCapabilityExecutionRecorder(jdbcTemplate);
        reader = new JdbcCapabilityExecutionReader(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    /** One execution, recorded, so a tree can be built without repeating the builder five times. */
    private void record(String id, String parent) {
        record(id, parent, Instant.now());
    }

    /** The same, with a chosen start time, for the tests that are about ordering. */
    private void record(String id, String parent, Instant startedAt) {
        Instant now = startedAt;
        recorder.record(CapabilityExecution.builder("cap-" + id, "1")
                .executionId(id)
                .parentExecutionId(parent)
                .startedAt(now)
                .finishedAt(now.plusMillis(1))
                .durationMs(1)
                .success(true)
                .build());
    }

    /**
     * The recursive CTE, against a real database rather than the in-memory store.
     *
     * <p>Three levels deep on purpose: two would pass against the direct-children query this replaces. The
     * second tree is here because a traversal keyed on the wrong column returns the whole table and still
     * looks right when the table holds one tree.
     */
    @Test
    void subtreeReturnsEveryDescendantInOneQuery() {
        record("root", null);
        record("a", "root");
        record("b", "root");
        record("a1", "a");
        record("a1x", "a1");
        record("elsewhere", null);
        record("elsewhere-child", "elsewhere");

        List<String> walked = reader.subtree("root").stream()
                .map(CapabilityExecution::executionId).toList();

        assertThat(walked).containsExactlyInAnyOrder("root", "a", "b", "a1", "a1x");
        assertThat(walked).doesNotContain("elsewhere", "elsewhere-child");

        // Ordering is the contract the nested view is built from: parents before children, root first.
        assertThat(walked).first().isEqualTo("root");
        assertThat(walked.indexOf("a")).isLessThan(walked.indexOf("a1"));
        assertThat(walked.indexOf("a1")).isLessThan(walked.indexOf("a1x"));
    }

    /**
     * The same sibling order the in-memory store produces — which is the bug this pair of tests exists for.
     *
     * <p>The CTE ordered siblings ascending while {@code SELECT_CHILDREN} and the in-memory store are both
     * most-recent-first, so the two implementations of one interface method disagreed and nothing failed:
     * the original tests asserted contents and parent-before-child, neither of which notices. Raised in
     * review.
     */
    @Test
    void subtreeOrdersSiblingsMostRecentFirst() {
        Instant base = Instant.parse("2026-09-04T00:00:00Z");
        record("root", null, base);
        record("first", "root", base.plusMillis(10));
        record("second", "root", base.plusMillis(20));
        record("third", "root", base.plusMillis(30));

        assertThat(reader.subtree("root").stream().map(CapabilityExecution::executionId).toList())
                .containsExactly("root", "third", "second", "first");
    }

    /** Equal timestamps tie-break on id, so the order is fixed rather than whatever the plan produced. */
    @Test
    void subtreeBreaksTiesOnId() {
        Instant same = Instant.parse("2026-09-04T00:00:00Z");
        record("root", null, same);
        record("c-charlie", "root", same);
        record("a-alpha", "root", same);
        record("b-bravo", "root", same);

        assertThat(reader.subtree("root").stream().map(CapabilityExecution::executionId).toList())
                .containsExactly("root", "a-alpha", "b-bravo", "c-charlie");
    }

    /**
     * A cycle returns each execution ONCE.
     *
     * <p>The CTE bounds depth but does not remember where it has been, so a cycle yields the same rows once
     * per lap until the bound is reached — fifty laps of three nodes. The in-memory store dedupes through its
     * visited set, so without this the JDBC implementation alone would break the interface's promise of "the
     * root and every descendant", and a consumer nesting the result would attach children to whichever copy
     * it read last. Raised in review.
     */
    @Test
    void subtreeReturnsEachNodeOnceEvenWhenParentLinksCycle() {
        Instant base = Instant.parse("2026-09-04T00:00:00Z");
        record("c-root", "c-leaf", base);
        record("c-mid", "c-root", base.plusMillis(10));
        record("c-leaf", "c-mid", base.plusMillis(20));

        List<String> walked = reader.subtree("c-root").stream()
                .map(CapabilityExecution::executionId).toList();

        assertThat(walked).containsExactly("c-root", "c-mid", "c-leaf");
        assertThat(walked).doesNotHaveDuplicates();
    }

    @Test
    void subtreeOfAnUnknownIdIsEmpty() {
        record("root", null);

        assertThat(reader.subtree("no-such-execution")).isEmpty();
        assertThat(reader.subtree(null)).isEmpty();
    }

    /**
     * A subtree still carries each node's model invocations, which the CTE selects none of — they are
     * hydrated per row afterwards, and a traversal that returned bare rows would quietly lose the cost data
     * that is most of the reason to look at a tree.
     */
    @Test
    void subtreeKeepsEachNodesModelInvocations() {
        Instant now = Instant.now();
        record("root", null);
        recorder.record(CapabilityExecution.builder("cap-child", "1")
                .executionId("child")
                .parentExecutionId("root")
                .startedAt(now)
                .finishedAt(now.plusMillis(2))
                .durationMs(2)
                .success(true)
                .addModelInvocation(new ModelInvocation("claude", 100, 25, new BigDecimal("0.01"), now))
                .build());

        CapabilityExecution child = reader.subtree("root").stream()
                .filter(e -> "child".equals(e.executionId())).findFirst().orElseThrow();

        assertThat(child.modelInvocations()).hasSize(1);
        assertThat(child.modelInvocations().get(0).model()).isEqualTo("claude");
    }

    @Test
    void persistsExecutionWithModelInvocationsAndReadsItBack() {
        Instant now = Instant.now();
        CapabilityExecution execution = CapabilityExecution.builder("Generate Course", "1")
                .executionId("exec-1")
                .domain("academy")
                .principal("user-42")
                .startedAt(now)
                .finishedAt(now.plusMillis(120))
                .durationMs(120)
                .success(true)
                .addModelInvocation(new ModelInvocation("claude", 100, 25, new BigDecimal("0.01"), now))
                .addModelInvocation(new ModelInvocation("nova", 200, 50, new BigDecimal("0.02"), now))
                .build();

        recorder.record(execution);

        Optional<CapabilityExecution> loaded = reader.byId("exec-1");
        assertThat(loaded).isPresent();
        CapabilityExecution durable = loaded.get();
        assertThat(durable.principal()).isEqualTo("user-42");
        assertThat(durable.modelInvocations()).hasSize(2);
        assertThat(durable.inputTokens()).isEqualTo(300);
        assertThat(durable.outputTokens()).isEqualTo(75);
        assertThat(durable.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(durable.model()).isEqualTo("nova");
    }

    @Test
    void linksChildrenAndAggregatesDurableScorecards() {
        Instant now = Instant.now();
        recorder.record(CapabilityExecution.builder("Generate Course", "1")
                .executionId("parent-1").startedAt(now).finishedAt(now).durationMs(50).success(true)
                .addModelInvocation(new ModelInvocation("claude", 10, 5, new BigDecimal("0.001"), now))
                .build());
        recorder.record(CapabilityExecution.builder("Generate Lesson", "1")
                .executionId("child-1").parentExecutionId("parent-1")
                .startedAt(now).finishedAt(now).durationMs(30).success(true)
                .addModelInvocation(new ModelInvocation("claude", 20, 10, new BigDecimal("0.002"), now))
                .build());

        List<CapabilityExecution> children = reader.childrenOf("parent-1");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).capabilityName()).isEqualTo("Generate Lesson");

        List<CapabilityScorecard> scorecards = reader.scorecards();
        assertThat(scorecards).hasSize(2);
        assertThat(scorecards).anyMatch(sc -> sc.name().equals("Generate Course") && sc.invocations() == 1);
    }

    @Test
    void retentionPurgeRemovesOldExecutions() {
        Instant old = Instant.now().minusSeconds(60L * 60 * 24 * 30); // 30 days ago
        recorder.record(CapabilityExecution.builder("Old", "1")
                .executionId("old-1").startedAt(old).finishedAt(old).durationMs(10).success(true)
                .addModelInvocation(new ModelInvocation("claude", 1, 1, null, old))
                .build());

        CapsteadJdbcRetentionCleaner cleaner = new CapsteadJdbcRetentionCleaner(new JdbcTemplate(database), 7);
        int removed = cleaner.purge();

        assertThat(removed).isEqualTo(1);
        assertThat(reader.byId("old-1")).isEmpty();
    }
}
