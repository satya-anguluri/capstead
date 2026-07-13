package io.capstead.jdbc;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Applies the Capstead-owned schema at startup. The DDL is idempotent ({@code CREATE TABLE IF NOT
 * EXISTS}), so Capstead manages its own tables without depending on the application's migration tool.
 *
 * <p>The script is chosen by database vendor: MySQL/MariaDB get {@code capstead-schema-mysql.sql}
 * (AUTO_INCREMENT + inline indexes), while PostgreSQL and H2 use the default
 * {@code capstead-schema.sql} ({@code GENERATED ... AS IDENTITY}). Unknown vendors fall back to the
 * default.
 */
public class CapsteadSchemaInitializer implements InitializingBean {

    private static final String DEFAULT_SCRIPT = "io/capstead/jdbc/capstead-schema.sql";

    private final DataSource dataSource;

    public CapsteadSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource(resolveSchemaScript()));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    private String resolveSchemaScript() {
        String vendor = detectVendor();
        if (vendor != null) {
            String vendorScript = "io/capstead/jdbc/capstead-schema-" + vendor + ".sql";
            if (new ClassPathResource(vendorScript).exists()) {
                return vendorScript;
            }
        }
        return DEFAULT_SCRIPT;
    }

    private String detectVendor() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null) {
                return null;
            }
            String name = product.toLowerCase(Locale.ROOT);
            if (name.contains("mysql") || name.contains("mariadb")) {
                return "mysql";
            }
            if (name.contains("postgresql")) {
                return "postgresql";
            }
            if (name.contains("h2")) {
                return "h2";
            }
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }
}
