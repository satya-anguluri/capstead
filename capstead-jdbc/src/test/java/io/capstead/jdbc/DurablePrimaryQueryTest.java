package io.capstead.jdbc;

import io.capstead.runtime.CapabilityExecutionQuery;
import io.capstead.runtime.InMemoryCapabilityExecutionStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When {@code capstead.jdbc.enabled=true}, the durable {@link JdbcCapabilityExecutionReader} must be
 * the <em>primary</em> {@link CapabilityExecutionQuery}, so the actuator scorecard / execution
 * endpoints read cross-instance durable data instead of the per-instance in-memory store.
 */
class DurablePrimaryQueryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, CapsteadJdbcAutoConfiguration.class))
            .withUserConfiguration(InMemoryStoreConfig.class)
            .withPropertyValues("capstead.jdbc.enabled=true");

    @Test
    void jdbcReaderIsThePrimaryExecutionQuery() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(CapabilityExecutionQuery.class)).hasSizeGreaterThanOrEqualTo(2);
            assertThat(context.getBean(CapabilityExecutionQuery.class))
                    .isInstanceOf(JdbcCapabilityExecutionReader.class);
        });
    }

    @Test
    void withoutJdbcTheInMemoryStoreIsTheQuery() {
        new ApplicationContextRunner()
                .withUserConfiguration(InMemoryStoreConfig.class)
                .run(context -> assertThat(context.getBean(CapabilityExecutionQuery.class))
                        .isInstanceOf(InMemoryCapabilityExecutionStore.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class InMemoryStoreConfig {
        @Bean
        InMemoryCapabilityExecutionStore capabilityExecutionStore() {
            return new InMemoryCapabilityExecutionStore();
        }
    }
}
