package io.capstead.jdbc;

import org.springframework.beans.factory.ObjectProvider;
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
 * {@link DataSource}, so its tables live alongside the app's. To keep them in a separate schema,
 * declare a bean named {@code capsteadDataSource} pointing there — every Capstead table operation then
 * uses it. Capstead does <em>not</em> register a fallback DataSource bean (that would give the context
 * two DataSources and disable JPA auto-configuration); when no {@code capsteadDataSource} exists it
 * simply resolves the primary DataSource. If you <em>do</em> declare a {@code capsteadDataSource}, mark
 * your application's main DataSource {@code @Primary} as usual for multi-DataSource apps.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "capstead.jdbc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CapsteadJdbcProperties.class)
public class CapsteadJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapsteadSchemaInitializer capsteadSchemaInitializer(
            ObjectProvider<DataSource> dataSource,
            @Qualifier("capsteadDataSource") ObjectProvider<DataSource> capsteadDataSource) {
        return new CapsteadSchemaInitializer(resolve(dataSource, capsteadDataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionRecorder jdbcCapabilityExecutionRecorder(
            ObjectProvider<DataSource> dataSource,
            @Qualifier("capsteadDataSource") ObjectProvider<DataSource> capsteadDataSource) {
        return new JdbcCapabilityExecutionRecorder(new JdbcTemplate(resolve(dataSource, capsteadDataSource)));
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public JdbcCapabilityExecutionReader jdbcCapabilityExecutionReader(
            ObjectProvider<DataSource> dataSource,
            @Qualifier("capsteadDataSource") ObjectProvider<DataSource> capsteadDataSource) {
        return new JdbcCapabilityExecutionReader(new JdbcTemplate(resolve(dataSource, capsteadDataSource)));
    }

    @Bean
    @ConditionalOnMissingBean
    public CapsteadJdbcRetentionCleaner capsteadJdbcRetentionCleaner(
            ObjectProvider<DataSource> dataSource,
            @Qualifier("capsteadDataSource") ObjectProvider<DataSource> capsteadDataSource,
            CapsteadJdbcProperties properties) {
        return new CapsteadJdbcRetentionCleaner(
                new JdbcTemplate(resolve(dataSource, capsteadDataSource)), properties.getRetentionDays());
    }

    /**
     * The dedicated {@code capsteadDataSource} bean when present, otherwise the application's primary
     * DataSource. Resolving here (rather than registering a fallback bean) keeps the context to a
     * single DataSource when no dedicated one is declared, so JPA auto-configuration is unaffected.
     */
    private static DataSource resolve(ObjectProvider<DataSource> primary,
                                      ObjectProvider<DataSource> capsteadDataSource) {
        DataSource dedicated = capsteadDataSource.getIfAvailable();
        return dedicated != null ? dedicated : primary.getObject();
    }
}
