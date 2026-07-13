package io.capstead.jdbc;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Enables durable, cross-instance capability metrics by persisting executions to Capstead-owned
 * tables. Opt-in via {@code capstead.jdbc.enabled=true}, and only when a {@link DataSource} exists.
 *
 * <p>Registering {@link JdbcCapabilityExecutionRecorder} as a bean is enough — the interceptor fans
 * every execution out to it automatically, alongside the in-memory store. {@link JdbcCapabilityExecutionReader}
 * serves the durable scorecard and execution history; {@link CapsteadJdbcRetentionCleaner} trims old
 * rows. Capstead creates its own schema on startup.
 *
 * <p><strong>Isolating Capstead's tables:</strong> by default Capstead uses the application's primary
 * {@link DataSource}, so its tables live alongside the app's. To keep them in a separate schema (or
 * database, or even a different server), declare a bean named {@code capsteadDataSource} pointing there
 * — every Capstead table operation then uses it. The default {@link #capsteadDataSource(DataSource)}
 * bean backs off automatically when you provide your own.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "capstead.jdbc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CapsteadJdbcProperties.class)
public class CapsteadJdbcAutoConfiguration {

    /**
     * The {@link DataSource} Capstead persists to. Defaults to the application's primary DataSource;
     * override by declaring your own bean named {@code capsteadDataSource} (e.g. pointing at a
     * dedicated {@code capstead} schema).
     */
    @Bean
    @ConditionalOnMissingBean(name = "capsteadDataSource")
    public DataSource capsteadDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public CapsteadSchemaInitializer capsteadSchemaInitializer(
            @Qualifier("capsteadDataSource") DataSource dataSource) {
        return new CapsteadSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionRecorder jdbcCapabilityExecutionRecorder(
            @Qualifier("capsteadDataSource") DataSource dataSource) {
        return new JdbcCapabilityExecutionRecorder(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionReader jdbcCapabilityExecutionReader(
            @Qualifier("capsteadDataSource") DataSource dataSource) {
        return new JdbcCapabilityExecutionReader(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public CapsteadJdbcRetentionCleaner capsteadJdbcRetentionCleaner(
            @Qualifier("capsteadDataSource") DataSource dataSource,
            CapsteadJdbcProperties properties) {
        return new CapsteadJdbcRetentionCleaner(new JdbcTemplate(dataSource), properties.getRetentionDays());
    }
}
