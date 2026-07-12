package io.capstead.jdbc;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Applies the Capstead-owned schema ({@code capstead-schema.sql}) at startup. The DDL is idempotent
 * ({@code CREATE TABLE/INDEX IF NOT EXISTS}), so Capstead manages its own table without depending on
 * the application's migration tool.
 */
public class CapsteadSchemaInitializer implements InitializingBean {

    private final DataSource dataSource;

    public CapsteadSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("io/capstead/jdbc/capstead-schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
