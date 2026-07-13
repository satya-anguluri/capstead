package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the MySQL schema variant applies and the recorder/reader round-trip on a real MySQL
 * (via Testcontainers). Auto-skips when Docker is unavailable, so it never breaks a Docker-less build.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcMySqlRoundTripTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private JdbcTemplate jdbcTemplate;
    private JdbcCapabilityExecutionRecorder recorder;
    private JdbcCapabilityExecutionReader reader;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        dataSource.setDriverClassName(MYSQL.getDriverClassName());

        // Vendor detection should select capstead-schema-mysql.sql for a MySQL connection.
        new CapsteadSchemaInitializer(dataSource).afterPropertiesSet();

        jdbcTemplate = new JdbcTemplate(dataSource);
        recorder = new JdbcCapabilityExecutionRecorder(jdbcTemplate);
        reader = new JdbcCapabilityExecutionReader(jdbcTemplate);
    }

    @Test
    void persistsExecutionWithInvocationsAndReadsItBack() {
        Instant now = Instant.now();
        recorder.record(CapabilityExecution.builder("Generate Course", "1")
                .executionId("m-1")
                .domain("academy")
                .principal("user-9")
                .startedAt(now)
                .finishedAt(now.plusMillis(80))
                .durationMs(80)
                .success(true)
                .addModelInvocation(new ModelInvocation("claude", 100, 25, new BigDecimal("0.01"), now))
                .addModelInvocation(new ModelInvocation("nova", 200, 50, new BigDecimal("0.02"), now))
                .build());

        Optional<CapabilityExecution> loaded = reader.byId("m-1");
        assertThat(loaded).isPresent();
        CapabilityExecution durable = loaded.get();
        assertThat(durable.principal()).isEqualTo("user-9");
        assertThat(durable.modelInvocations()).hasSize(2);
        assertThat(durable.inputTokens()).isEqualTo(300);
        assertThat(durable.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.03"));

        List<CapabilityScorecard> scorecards = reader.scorecards();
        assertThat(scorecards).anyMatch(s -> s.name().equals("Generate Course") && s.invocations() == 1);
    }

    @Test
    void linksChildrenAndRetentionPurgeWork() {
        Instant now = Instant.now();
        recorder.record(CapabilityExecution.builder("Parent", "1")
                .executionId("p-1").startedAt(now).finishedAt(now).durationMs(10).success(true)
                .addModelInvocation(new ModelInvocation("claude", 10, 5, new BigDecimal("0.001"), now))
                .build());
        recorder.record(CapabilityExecution.builder("Child", "1")
                .executionId("c-1").parentExecutionId("p-1").startedAt(now).finishedAt(now).durationMs(5).success(true)
                .addModelInvocation(new ModelInvocation("claude", 20, 10, new BigDecimal("0.002"), now))
                .build());

        assertThat(reader.childrenOf("p-1")).extracting(CapabilityExecution::capabilityName).containsExactly("Child");

        // Old execution should be purged; recent one retained.
        Instant old = Instant.now().minus(40, ChronoUnit.DAYS);
        recorder.record(CapabilityExecution.builder("Old", "1")
                .executionId("o-1").startedAt(old).finishedAt(old).durationMs(1).success(true)
                .addModelInvocation(new ModelInvocation("claude", 1, 1, null, old))
                .build());

        int removed = new CapsteadJdbcRetentionCleaner(jdbcTemplate, 7).purge();
        assertThat(removed).isEqualTo(1);
        assertThat(reader.byId("o-1")).isEmpty();
        assertThat(reader.byId("p-1")).isPresent();
    }
}
