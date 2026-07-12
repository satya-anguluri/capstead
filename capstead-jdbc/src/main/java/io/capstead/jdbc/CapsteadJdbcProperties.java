package io.capstead.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the durable JDBC recorder.
 *
 * <pre>
 * capstead.jdbc.enabled: true          # opt in to durable persistence
 * capstead.jdbc.retention-days: 90     # purge executions older than N days (0 = keep forever)
 * </pre>
 */
@ConfigurationProperties("capstead.jdbc")
public class CapsteadJdbcProperties {

    /** Opt in to persisting executions to the database. */
    private boolean enabled = false;

    /** Delete executions (and their invocations) older than this many days. {@code 0} disables purging. */
    private int retentionDays = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
