package io.capstead.jdbc;

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
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "capstead.jdbc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CapsteadJdbcProperties.class)
public class CapsteadJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapsteadSchemaInitializer capsteadSchemaInitializer(DataSource dataSource) {
        return new CapsteadSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionRecorder jdbcCapabilityExecutionRecorder(DataSource dataSource) {
        return new JdbcCapabilityExecutionRecorder(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionReader jdbcCapabilityExecutionReader(DataSource dataSource) {
        return new JdbcCapabilityExecutionReader(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public CapsteadJdbcRetentionCleaner capsteadJdbcRetentionCleaner(DataSource dataSource,
                                                                     CapsteadJdbcProperties properties) {
        return new CapsteadJdbcRetentionCleaner(new JdbcTemplate(dataSource), properties.getRetentionDays());
    }
}
