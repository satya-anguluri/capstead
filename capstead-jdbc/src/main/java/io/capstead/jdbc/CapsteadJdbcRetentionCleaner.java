package io.capstead.jdbc;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Purges executions (and their model invocations) older than the configured retention window, so the
 * Capstead tables do not grow without bound. Runs a daily sweep on a daemon scheduler; a no-op when
 * {@code capstead.jdbc.retention-days} is {@code 0}.
 */
public class CapsteadJdbcRetentionCleaner implements InitializingBean, DisposableBean {

    private static final Logger log = System.getLogger(CapsteadJdbcRetentionCleaner.class.getName());

    private static final String DELETE_INVOCATIONS = """
            DELETE FROM capstead_model_invocation
            WHERE execution_id IN (SELECT execution_id FROM capstead_execution WHERE started_at < ?)
            """;
    private static final String DELETE_EXECUTIONS = "DELETE FROM capstead_execution WHERE started_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;
    private ScheduledExecutorService scheduler;

    public CapsteadJdbcRetentionCleaner(JdbcTemplate jdbcTemplate, int retentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
    }

    @Override
    public void afterPropertiesSet() {
        if (retentionDays <= 0) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "capstead-jdbc-retention");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::purgeQuietly, 1, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
    }

    private void purgeQuietly() {
        try {
            int removed = purge();
            if (removed > 0) {
                log.log(Level.INFO, "[capstead] purged {0} executions older than {1} days", removed, retentionDays);
            }
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "[capstead] retention purge failed: {0}", ex.getMessage());
        }
    }

    /** Deletes executions older than the retention window; returns the number of executions removed. */
    public int purge() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        jdbcTemplate.update(DELETE_INVOCATIONS, cutoff);
        return jdbcTemplate.update(DELETE_EXECUTIONS, cutoff);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
