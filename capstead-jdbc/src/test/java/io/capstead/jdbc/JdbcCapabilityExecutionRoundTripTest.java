package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;

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

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("io/capstead/jdbc/capstead-schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        recorder = new JdbcCapabilityExecutionRecorder(jdbcTemplate);
        reader = new JdbcCapabilityExecutionReader(jdbcTemplate);
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
