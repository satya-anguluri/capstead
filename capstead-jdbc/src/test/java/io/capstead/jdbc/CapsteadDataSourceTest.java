package io.capstead.jdbc;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A dedicated {@code capsteadDataSource} bean keeps Capstead's tables isolated in their own schema:
 * the schema initializer + recorder + reader all target it, not the application's primary DataSource.
 */
class CapsteadDataSourceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CapsteadJdbcAutoConfiguration.class))
            .withPropertyValues("capstead.jdbc.enabled=true");

    @Test
    void createsCapsteadTablesInTheDedicatedDataSourceOnly() {
        runner.withUserConfiguration(TwoDataSources.class).run(context -> {
            assertThat(context).hasNotFailed();

            JdbcTemplate capstead = new JdbcTemplate(context.getBean("capsteadDataSource", DataSource.class));
            assertThat(capstead.queryForObject("SELECT COUNT(*) FROM capstead_execution", Integer.class)).isZero();

            JdbcTemplate primary = new JdbcTemplate(context.getBean("primaryDataSource", DataSource.class));
            assertThatThrownBy(() -> primary.queryForObject("SELECT COUNT(*) FROM capstead_execution", Integer.class))
                    .isInstanceOf(Exception.class);
        });
    }

    @Test
    void fallsBackToPrimaryDataSourceWhenNoDedicatedOneProvided() {
        runner.withUserConfiguration(SingleDataSource.class).run(context -> {
            assertThat(context).hasNotFailed();
            JdbcTemplate primary = new JdbcTemplate(context.getBean("primaryDataSource", DataSource.class));
            // With no dedicated bean, Capstead uses the primary DataSource, so its tables are there.
            assertThat(primary.queryForObject("SELECT COUNT(*) FROM capstead_execution", Integer.class)).isZero();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSources {
        @Bean
        @Primary
        DataSource primaryDataSource() {
            return h2("mkt-" + UUID.randomUUID());
        }

        @Bean
        DataSource capsteadDataSource() {
            return h2("cap-" + UUID.randomUUID());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleDataSource {
        @Bean
        @Primary
        DataSource primaryDataSource() {
            return h2("only-" + UUID.randomUUID());
        }
    }

    private static DataSource h2(String name) {
        return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName(name).build();
    }
}
