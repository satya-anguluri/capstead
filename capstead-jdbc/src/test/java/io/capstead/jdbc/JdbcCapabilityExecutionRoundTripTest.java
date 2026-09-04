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
        Instant now = Instant.now();
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
