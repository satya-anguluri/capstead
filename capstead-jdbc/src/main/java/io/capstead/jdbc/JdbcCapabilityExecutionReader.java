package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Reads durable capability history back from the Capstead-owned tables — the cross-instance,
 * restart-surviving counterpart to the in-memory store. An application can point its
 * {@code /actuator/capabilityscorecard} and execution views at this reader to serve durable numbers.
 */
public class JdbcCapabilityExecutionReader {

    private static final String SCORECARDS = """
            SELECT capability_name,
                   version,
                   COUNT(*)                                        AS invocations,
                   AVG(CASE WHEN success THEN 1.0 ELSE 0.0 END)    AS success_rate,
                   AVG(duration_ms)                                AS avg_latency_ms,
                   AVG(total_input_tokens)                         AS avg_input_tokens,
                   AVG(total_output_tokens)                        AS avg_output_tokens,
                   AVG(total_cost)                                 AS avg_cost,
                   COALESCE(SUM(total_cost), 0)                    AS total_cost
            FROM capstead_execution
            GROUP BY capability_name, version
            ORDER BY capability_name, version
            """;

    private static final String SELECT_EXECUTION = """
            SELECT execution_id, parent_execution_id, capability_name, version, domain, principal,
                   started_at, finished_at, duration_ms, success, error_type, retries,
                   captured_input, captured_output
            FROM capstead_execution
            WHERE execution_id = ?
            """;

    private static final String SELECT_RECENT = """
            SELECT execution_id, parent_execution_id, capability_name, version, domain, principal,
                   started_at, finished_at, duration_ms, success, error_type, retries,
                   captured_input, captured_output
            FROM capstead_execution
            ORDER BY started_at DESC
            """;

    private static final String SELECT_CHILDREN = """
            SELECT execution_id, parent_execution_id, capability_name, version, domain, principal,
                   started_at, finished_at, duration_ms, success, error_type, retries,
                   captured_input, captured_output
            FROM capstead_execution
            WHERE parent_execution_id = ?
            ORDER BY started_at DESC
            """;

    private static final String SELECT_INVOCATIONS = """
            SELECT model, input_tokens, output_tokens, estimated_cost, invoked_at
            FROM capstead_model_invocation
            WHERE execution_id = ?
            ORDER BY seq
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCapabilityExecutionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Aggregated scorecards for every capability seen, across all instances and restarts. */
    public List<CapabilityScorecard> scorecards() {
        return jdbcTemplate.query(SCORECARDS, (rs, rowNum) -> new CapabilityScorecard(
                rs.getString("capability_name"),
                rs.getString("version"),
                rs.getLong("invocations"),
                rs.getDouble("success_rate"),
                rs.getDouble("avg_latency_ms"),
                rs.getDouble("avg_input_tokens"),
                rs.getDouble("avg_output_tokens"),
                rs.getDouble("avg_cost"),
                rs.getBigDecimal("total_cost")));
    }

    /** The durable execution with the given id, with its model invocations rehydrated. */
    public Optional<CapabilityExecution> byId(String executionId) {
        List<CapabilityExecution.Builder> builders = jdbcTemplate.query(SELECT_EXECUTION, executionRowMapper(), executionId);
        if (builders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(hydrate(builders.get(0), executionId));
    }

    /** Most-recent-first durable execution history. */
    public List<CapabilityExecution> recent() {
        return jdbcTemplate.query(SELECT_RECENT, executionRowMapper()).stream()
                .map(builder -> hydrate(builder, builder.executionId()))
                .toList();
    }

    /** Most-recent-first durable direct children of the given execution. */
    public List<CapabilityExecution> childrenOf(String parentExecutionId) {
        return jdbcTemplate.query(SELECT_CHILDREN, executionRowMapper(), parentExecutionId).stream()
                .map(builder -> hydrate(builder, builder.executionId()))
                .toList();
    }

    private CapabilityExecution hydrate(CapabilityExecution.Builder builder, String executionId) {
        jdbcTemplate.query(SELECT_INVOCATIONS, (rs, rowNum) -> {
            builder.addModelInvocation(new ModelInvocation(
                    rs.getString("model"),
                    rs.getInt("input_tokens"),
                    rs.getInt("output_tokens"),
                    rs.getBigDecimal("estimated_cost"),
                    toInstant(rs.getTimestamp("invoked_at"))));
            return null;
        }, executionId);
        return builder.build();
    }

    private RowMapper<CapabilityExecution.Builder> executionRowMapper() {
        return (rs, rowNum) -> CapabilityExecution
                .builder(rs.getString("capability_name"), rs.getString("version"))
                .executionId(rs.getString("execution_id"))
                .parentExecutionId(rs.getString("parent_execution_id"))
                .domain(rs.getString("domain"))
                .principal(rs.getString("principal"))
                .startedAt(toInstant(rs.getTimestamp("started_at")))
                .finishedAt(toInstant(rs.getTimestamp("finished_at")))
                .durationMs(rs.getLong("duration_ms"))
                .success(rs.getBoolean("success"))
                .errorType(rs.getString("error_type"))
                .retries(rs.getInt("retries"))
                .capturedInput(rs.getString("captured_input"))
                .capturedOutput(rs.getString("captured_output"));
    }

    private static java.time.Instant toInstant(Timestamp timestamp) throws SQLException {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
