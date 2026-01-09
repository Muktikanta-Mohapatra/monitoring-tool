package com.monitoring.logforwarder.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Configuration class for ClickHouse database connectivity.
 *
 * <p><b>Purpose:</b> Configures the ClickHouse data source, entity manager, and transaction
 * manager for time-series event storage. ClickHouse is the primary storage for high-volume
 * log events due to its columnar storage and fast aggregation capabilities.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses HikariCP connection pool for efficient connection management</li>
 *   <li>Configured as PRIMARY data source (events stored here by default)</li>
 *   <li>No-op transaction manager since ClickHouse doesn't support transactions</li>
 *   <li>Batch inserts optimized with configurable batch/fetch sizes</li>
 *   <li>Prepared statement caching enabled for query performance</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>{@code spring.datasource.clickhouse.jdbc-url} - ClickHouse JDBC URL</li>
 *   <li>{@code spring.datasource.clickhouse.username/password} - Credentials</li>
 *   <li>{@code spring.datasource.clickhouse.hikari.*} - Connection pool settings</li>
 *   <li>{@code app.batch.jdbc-batch-size} - Hibernate batch insert size (default: 1000)</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ClickHouseNoOpTransactionManager
 * @see com.monitoring.logforwarder.repository.clickhouse.ClickHouseEventRepository
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.monitoring.logforwarder.repository.clickhouse",
    entityManagerFactoryRef = "clickhouseEntityManagerFactory",
    transactionManagerRef = "clickhouseTransactionManager"
)
public class ClickHouseDataSourceConfig {

    @Value("${spring.datasource.clickhouse.jdbc-url}")
    private String jdbcUrl;

    @Value("${spring.datasource.clickhouse.username}")
    private String username;

    @Value("${spring.datasource.clickhouse.password}")
    private String password;

    @Value("${spring.datasource.clickhouse.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.clickhouse.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.clickhouse.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${app.batch.jdbc-batch-size:1000}")
    private int batchSize;

    @Value("${app.batch.jdbc-fetch-size:500}")
    private int fetchSize;

    @Primary
    @Bean(name = "clickhouseDataSource")
    public DataSource clickhouseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(true);
        config.setPoolName("ClickHousePool");
        config.addDataSourceProperty("cachePreparedStatements", "true");
        config.addDataSourceProperty("preparedStatementCacheSize", "250");
        config.addDataSourceProperty("preparedStatementCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(name = "clickhouseEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean clickhouseEntityManagerFactory(
            @Qualifier("clickhouseDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.monitoring.logforwarder.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");
        properties.setProperty("hibernate.jdbc.batch_size", String.valueOf(batchSize));
        properties.setProperty("hibernate.jdbc.fetch_size", String.valueOf(fetchSize));
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        properties.setProperty("hibernate.generate_statistics", "false");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", "true");
        properties.setProperty("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT");
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        properties.setProperty("hibernate.connection.autocommit", "true");
        
        em.setJpaProperties(properties);
        return em;
    }

    @Primary
    @Bean(name = "clickhouseTransactionManager")
    public PlatformTransactionManager clickhouseTransactionManager(
            @Qualifier("clickhouseEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new ClickHouseNoOpTransactionManager(entityManagerFactory);
    }
}
