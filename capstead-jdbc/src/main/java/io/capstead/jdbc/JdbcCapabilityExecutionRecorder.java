package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;
import io.capstead.runtime.CapabilityExecutionRecorder;

import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists every {@link CapabilityExecution} to the Capstead-owned tables — the execution (with
 * rolled-up totals) plus one {@code capstead_model_invocation} row per model call — so the scorecard,
 * execution tree and per-model cost breakdown survive restarts and aggregate across instances.
 *
 * <p>The execution and its invocations are written atomically in a single transaction. Recording is
 * best-effort: a database hiccup must never fail the business call, so write errors are logged and
 * swallowed. Registered as a {@link CapabilityExecutionRecorder}, it receives every execution
 * alongside the in-memory store and Micrometer.
 */
public class JdbcCapabilityExecutionRecorder implements CapabilityExecutionRecorder {

    private static final Logger log = System.getLogger(JdbcCapabilityExecutionRecorder.class.getName());

    private static final String INSERT_EXECUTION = """
            INSERT INTO capstead_execution
                (execution_id, parent_execution_id, capability_name, version, domain, principal,
                 started_at, finished_at, duration_ms, success, error_type, retries,
                 model_invocations, total_input_tokens, total_output_tokens, total_cost,
                 captured_input, captured_output)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_INVOCATION = """
            INSERT INTO capstead_model_invocation
                (execution_id, seq, model, input_tokens, output_tokens, estimated_cost, invoked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCapabilityExecutionRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(CapabilityExecution execution) {
        if (execution.executionId() == null) {
            log.log(Level.WARNING, "[capstead] skipping persistence of execution ''{0}'' with no id", execution.coordinates());
            return;
        }
        try {
            jdbcTemplate.execute((java.sql.Connection connection) -> {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    insertExecution(connection, execution);
                    insertInvocations(connection, execution);
                    connection.commit();
                } catch (RuntimeException | java.sql.SQLException ex) {
                    connection.rollback();
                    throw ex instanceof java.sql.SQLException sql ? new RuntimeException(sql) : (RuntimeException) ex;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
                return null;
            });
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "[capstead] failed to persist execution for ''{0}'': {1}",
                    execution.coordinates(), ex.getMessage());
        }
    }

    private void insertExecution(java.sql.Connection connection, CapabilityExecution execution) throws java.sql.SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_EXECUTION)) {
            ps.setString(1, execution.executionId());
            ps.setString(2, execution.parentExecutionId());
            ps.setString(3, execution.capabilityName());
            ps.setString(4, execution.version());
            ps.setString(5, execution.domain());
            ps.setString(6, execution.principal());
            ps.setTimestamp(7, Timestamp.from(execution.startedAt()));
            ps.setTimestamp(8, Timestamp.from(execution.finishedAt()));
            ps.setLong(9, execution.durationMs());
            ps.setBoolean(10, execution.success());
            ps.setString(11, execution.errorType());
            ps.setInt(12, execution.retries());
            ps.setInt(13, execution.modelInvocationCount());
            ps.setLong(14, execution.inputTokens());
            ps.setLong(15, execution.outputTokens());
            ps.setBigDecimal(16, execution.estimatedCost());
            ps.setString(17, execution.capturedInput());
            ps.setString(18, execution.capturedOutput());
            ps.executeUpdate();
        }
    }

    private void insertInvocations(java.sql.Connection connection, CapabilityExecution execution) throws java.sql.SQLException {
        List<ModelInvocation> invocations = execution.modelInvocations();
        if (invocations.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(INSERT_INVOCATION)) {
            for (int seq = 0; seq < invocations.size(); seq++) {
                ModelInvocation invocation = invocations.get(seq);
                ps.setString(1, execution.executionId());
                ps.setInt(2, seq);
                ps.setString(3, invocation.model());
                ps.setInt(4, invocation.inputTokens());
                ps.setInt(5, invocation.outputTokens());
                ps.setBigDecimal(6, invocation.estimatedCost());
                ps.setTimestamp(7, invocation.invokedAt() == null ? null : Timestamp.from(invocation.invokedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
