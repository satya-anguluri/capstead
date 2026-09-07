package io.capstead.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ExecutionAttributes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Execution attributes through the JDBC recorder and reader, against H2 on the Capstead-owned schema.
 *
 * <p>A distinct database per test and shut down afterwards, for the reason documented at length on
 * {@code JdbcCapabilityExecutionRoundTripTest}: a shared in-memory H2 handle leaks rows between tests, and
 * the failure mode is tests that pass alone and fail together.
 */
@DisplayName("Execution attributes survive the database")
class JdbcExecutionAttributeRoundTripTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcCapabilityExecutionRecorder recorder;
    private JdbcCapabilityExecutionReader reader;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("io/capstead/jdbc/capstead-schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        recorder = new JdbcCapabilityExecutionRecorder(jdbcTemplate);
        reader = new JdbcCapabilityExecutionReader(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    private CapabilityExecution.Builder execution(String id) {
        Instant now = Instant.now();
        return CapabilityExecution.builder("Answer Question", "1")
                .executionId(id)
                .startedAt(now)
                .finishedAt(now.plusMillis(5))
                .durationMs(5)
                .success(true);
    }

    @Test
    @DisplayName("what was recorded is what comes back")
    void attributesRoundTrip() {
        recorder.record(execution("exec-1")
                .attribute("policy.authorization.outcome", "DENY")
                .attribute("policy.authorization.reason", "ACCESS_REVOKED")
                .attribute("evidence.sourceRevision", "17")
                .build());

        CapabilityExecution read = reader.byId("exec-1").orElseThrow();

        assertThat(read.attributes()).containsOnly(
                org.assertj.core.data.MapEntry.entry("policy.authorization.outcome", "DENY"),
                org.assertj.core.data.MapEntry.entry("policy.authorization.reason", "ACCESS_REVOKED"),
                org.assertj.core.data.MapEntry.entry("evidence.sourceRevision", "17"));
        assertThat(read.attribute("evidence.sourceRevision")).isEqualTo("17");
    }

    @Test
    @DisplayName("an execution with no attributes reads back with an empty map, not a failure")
    void noAttributesIsNotAFailure() {
        recorder.record(execution("exec-2").build());

        assertThat(reader.byId("exec-2").orElseThrow().attributes()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM capstead_execution_attribute", Integer.class)).isZero();
    }

    /**
     * EVERY read path, not just byId. All of them funnel through the reader's {@code hydrate}, and this is
     * what stops one of them being changed to bypass it without anybody noticing — an execution whose
     * attributes are missing looks like an execution that recorded none.
     */
    @Test
    @DisplayName("attributes come back from recent, recentFor, childrenOf and subtree too")
    void everyReadPathCarriesThem() {
        recorder.record(execution("parent-1").attribute("lab.fixture.version", "1").build());
        recorder.record(execution("child-1")
                .parentExecutionId("parent-1")
                .attribute("evidence.state", "STALE_EVIDENCE")
                .build());

        assertThat(reader.recent()).allSatisfy(e ->
                assertThat(e.attributes()).as("attributes on %s from recent()", e.executionId()).isNotEmpty());
        assertThat(reader.recentFor("Answer Question")).allSatisfy(e ->
                assertThat(e.attributes()).isNotEmpty());

        List<CapabilityExecution> children = reader.childrenOf("parent-1");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).attribute("evidence.state")).isEqualTo("STALE_EVIDENCE");

        assertThat(reader.subtree("parent-1"))
                .as("the subtree — the read path the labs use")
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.attributes()).isNotEmpty());
    }

    @Test
    @DisplayName("a value at the documented ceiling fits the column")
    void theColumnMatchesTheRule() {
        String atLimit = "x".repeat(ExecutionAttributes.MAX_VALUE_LENGTH);

        recorder.record(execution("exec-3").attribute("evidence.sourceRevision", atLimit).build());

        assertThat(reader.byId("exec-3").orElseThrow().attribute("evidence.sourceRevision"))
                .as("a value the rules accept must not be lost by the schema")
                .hasSize(ExecutionAttributes.MAX_VALUE_LENGTH);
    }

    /**
     * The query the table exists to serve: which executions decided a particular thing. Asserted through
     * SQL rather than the reader, because the index that makes it viable is on (name, value) and the point
     * is that this shape of question is answerable at all.
     */
    @Test
    @DisplayName("executions are findable by attribute name and value")
    void findableByNameAndValue() {
        recorder.record(execution("denied-1").attribute("policy.authorization.outcome", "DENY").build());
        recorder.record(execution("denied-2").attribute("policy.authorization.outcome", "DENY").build());
        recorder.record(execution("allowed-1").attribute("policy.authorization.outcome", "ALLOW").build());

        List<String> denied = jdbcTemplate.queryForList(
                "SELECT execution_id FROM capstead_execution_attribute WHERE attr_name = ? AND attr_value = ?"
                        + " ORDER BY execution_id",
                String.class, "policy.authorization.outcome", "DENY");

        assertThat(denied).containsExactly("denied-1", "denied-2");
    }

    @Test
    @DisplayName("re-recording the same execution id does not corrupt the row it already has")
    void thePrimaryKeyHoldsOneValuePerName() {
        recorder.record(execution("exec-4").attribute("evidence.state", "CURRENT").build());

        // The recorder logs and swallows a failed insert rather than throwing, so this is not an exception
        // test — it asserts that the first record survives the second attempt intact.
        recorder.record(execution("exec-4").attribute("evidence.state", "STALE_EVIDENCE").build());

        assertThat(reader.byId("exec-4").orElseThrow().attribute("evidence.state")).isEqualTo("CURRENT");
    }
}
