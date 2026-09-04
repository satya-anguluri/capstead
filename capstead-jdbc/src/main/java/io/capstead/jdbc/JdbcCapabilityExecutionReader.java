package io.capstead.jdbc;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityScorecard;
import io.capstead.core.ModelInvocation;
import io.capstead.runtime.CapabilityExecutionQuery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads durable capability history back from the Capstead-owned tables — the cross-instance,
 * restart-surviving counterpart to the in-memory store. An application can point its
 * {@code /actuator/capabilityscorecard} and execution views at this reader to serve durable numbers.
 */
public class JdbcCapabilityExecutionReader implements CapabilityExecutionQuery {

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

    private static final String SELECT_FOR_NAME = """
            SELECT execution_id, parent_execution_id, capability_name, version, domain, principal,
                   started_at, finished_at, duration_ms, success, error_type, retries,
                   captured_input, captured_output
            FROM capstead_execution
            WHERE capability_name = ?
            ORDER BY started_at DESC
            """;

    /**
     * The whole subtree in one round trip, breadth-first.
     *
     * <p>A recursive CTE, which both databases this reader is tested against support — H2 and MySQL 8. The
     * alternative is a query per level, which is what every caller was writing by hand.
     *
     * <p><b>{@code depth < ?} is the cycle guard and it is load-bearing.</b> Nothing validates that parent
     * links form a tree: they come from whatever recorded them, and a record whose ancestor claims it as a
     * parent produces a cycle that a recursive CTE will happily walk forever. MySQL would eventually raise
     * its own recursion limit as an error, and H2's behaviour is worse to rely on. Bounding it here means a
     * malformed graph returns a truncated tree instead of taking out the endpoint reading it.
     *
     * <p>{@code ORDER BY depth} gives the ordering the interface promises — root first, every node after its
     * parent — and matches what the in-memory store produces, so the two implementations agree.
     */
    private static final String SELECT_SUBTREE = """
            WITH RECURSIVE tree (execution_id, parent_execution_id, capability_name, version, domain, principal, started_at, finished_at, duration_ms, success, error_type, retries, captured_input, captured_output, depth) AS (
                SELECT execution_id, parent_execution_id, capability_name, version, domain, principal, started_at, finished_at, duration_ms, success, error_type, retries, captured_input, captured_output, 0 AS depth
                  FROM capstead_execution
                 WHERE execution_id = ?
                UNION ALL
                SELECT c.execution_id, c.parent_execution_id, c.capability_name, c.version, c.domain,
                       c.principal, c.started_at, c.finished_at, c.duration_ms, c.success, c.error_type,
                       c.retries, c.captured_input, c.captured_output, t.depth + 1
                  FROM capstead_execution c
                  JOIN tree t ON c.parent_execution_id = t.execution_id
                 WHERE t.depth < ?
            )
            SELECT execution_id, parent_execution_id, capability_name, version, domain, principal, started_at, finished_at, duration_ms, success, error_type, retries, captured_input, captured_output
              FROM tree
             ORDER BY depth, started_at
            """;

    /**
     * How deep a subtree may go before it is treated as malformed rather than merely deep.
     *
     * <p>Fifty is far past any real capability nesting — the deepest thing this records is an agent calling a
     * capability that calls another — and well short of the recursion limits the databases impose, so the
     * truncation is ours and predictable rather than theirs and a stack trace.
     */
    private static final int MAX_SUBTREE_DEPTH = 50;

    private static final String SELECT_INVOCATIONS = """
            SELECT model, input_tokens, output_tokens, estimated_cost, invoked_at
            FROM capstead_model_invocation
            WHERE execution_id = ?
            ORDER BY seq
            """;

    private static final String DISTINCT_MODELS = """
            SELECT e.capability_name, e.version, mi.model
            FROM capstead_execution e
            JOIN capstead_model_invocation mi ON e.execution_id = mi.execution_id
            WHERE mi.model IS NOT NULL
            GROUP BY e.capability_name, e.version, mi.model
            ORDER BY e.capability_name, e.version, mi.model
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCapabilityExecutionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Aggregated scorecards for every capability seen, across all instances and restarts. */
    public List<CapabilityScorecard> scorecards() {
        Map<String, List<String>> modelsByCoordinates = distinctModelsByCoordinates();
        return jdbcTemplate.query(SCORECARDS, (rs, rowNum) -> {
            String name = rs.getString("capability_name");
            String version = rs.getString("version");
            return new CapabilityScorecard(
                    name,
                    version,
                    rs.getLong("invocations"),
                    rs.getDouble("success_rate"),
                    rs.getDouble("avg_latency_ms"),
                    rs.getDouble("avg_input_tokens"),
                    rs.getDouble("avg_output_tokens"),
                    rs.getDouble("avg_cost"),
                    rs.getBigDecimal("total_cost"),
                    modelsByCoordinates.getOrDefault(name + "@" + version, List.of()));
        });
    }

    /** Distinct model ids used per capability@version, grouped in Java to stay vendor-neutral. */
    private Map<String, List<String>> distinctModelsByCoordinates() {
        Map<String, List<String>> models = new LinkedHashMap<>();
        jdbcTemplate.query(DISTINCT_MODELS, rs -> {
            String coordinates = rs.getString("capability_name") + "@" + rs.getString("version");
            models.computeIfAbsent(coordinates, key -> new ArrayList<>()).add(rs.getString("model"));
        });
        return models;
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

    /** Most-recent-first durable history for a single capability (all versions). */
    public List<CapabilityExecution> recentFor(String name) {
        return jdbcTemplate.query(SELECT_FOR_NAME, executionRowMapper(), name).stream()
                .map(builder -> hydrate(builder, builder.executionId()))
                .toList();
    }

    /** Most-recent-first durable direct children of the given execution. */
    public List<CapabilityExecution> childrenOf(String parentExecutionId) {
        return jdbcTemplate.query(SELECT_CHILDREN, executionRowMapper(), parentExecutionId).stream()
                .map(builder -> hydrate(builder, builder.executionId()))
                .toList();
    }

    @Override
    public List<CapabilityExecution> subtree(String executionId) {
        if (executionId == null) {
            return List.of();
        }
        return jdbcTemplate.query(SELECT_SUBTREE, executionRowMapper(), executionId, MAX_SUBTREE_DEPTH)
                .stream()
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
