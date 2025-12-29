package com.monitoring.logforwarder.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.orm.jpa.JpaTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.monitoring.logforwarder.repository.postgresql",
    entityManagerFactoryRef = "postgresqlEntityManagerFactory",
    transactionManagerRef = "postgresqlTransactionManager"
)
public class PostgreSQLDataSourceConfig {

    @Value("${spring.datasource.postgresql.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.postgresql.username}")
    private String username;

    @Value("${spring.datasource.postgresql.password}")
    private String password;

    @Value("${spring.datasource.postgresql.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.postgresql.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.postgresql.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Bean(name = "postgresqlDataSource")
    public DataSource postgresqlDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(true);
        config.setPoolName("PostgreSQLPool");
        config.addDataSourceProperty("cachePreparedStatements", "true");
        config.addDataSourceProperty("preparedStatementCacheSize", "250");
        config.addDataSourceProperty("preparedStatementCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }

    @Bean(name = "postgresqlEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresqlEntityManagerFactory(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.monitoring.logforwarder.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");
        properties.setProperty("hibernate.jdbc.batch_size", "100");
        properties.setProperty("hibernate.jdbc.fetch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        properties.setProperty("hibernate.generate_statistics", "false");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", "true");
        
        em.setJpaProperties(properties);
        return em;
    }

    @Bean(name = "postgresqlTransactionManager")
    public PlatformTransactionManager postgresqlTransactionManager(
            @Qualifier("postgresqlEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
