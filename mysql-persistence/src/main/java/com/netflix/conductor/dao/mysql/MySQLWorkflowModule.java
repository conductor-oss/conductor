package com.netflix.conductor.dao.mysql;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * @author mustafa
 */
public class MySQLWorkflowModule extends AbstractModule {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Provides
    @Singleton
    public DataSource getDataSource(Configuration config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getProperty("jdbc.url", "jdbc:mysql://localhost:3306/conductor"));
        dataSource.setUsername(config.getProperty("jdbc.username", "conductor"));
        dataSource.setPassword(config.getProperty("jdbc.password", "password"));
        dataSource.setAutoCommit(false);
        
        dataSource.setMaximumPoolSize(config.getIntProperty("jdbc.maxPoolSize", 20));
        dataSource.setMinimumIdle(config.getIntProperty("jdbc.minIdleSize", 5));
        dataSource.setIdleTimeout(config.getIntProperty("jdbc.idleTimeout", 1000*300));
        dataSource.setTransactionIsolation(config.getProperty("jdbc.isolationLevel", "TRANSACTION_REPEATABLE_READ"));
        
        flywayMigrate(config, dataSource);

        return dataSource;
    }

    @Override
    protected void configure() {
        bind(MetadataDAO.class).to(MySQLMetadataDAO.class);
        bind(ExecutionDAO.class).to(MySQLExecutionDAO.class);
        bind(QueueDAO.class).to(MySQLQueueDAO.class);
    }

    private void flywayMigrate(Configuration config, DataSource dataSource) {
        boolean enabled = getBool(config.getProperty("flyway.enabled", "true"), true);
        if(!enabled) {
            logger.debug("Flyway migrations are disabled");
            return;
        }

        String migrationTable = config.getProperty("flyway.table", null);

        Flyway flyway = new Flyway();
        if(null != migrationTable) {
            logger.debug("Using Flyway migration table '{}'", migrationTable);
            flyway.setTable(migrationTable);
        }

        flyway.setDataSource(dataSource);
        flyway.setPlaceholderReplacement(false);
        flyway.migrate();
    }

    private boolean getBool(String value, boolean defaultValue) {
        if(null == value || value.trim().length() == 0){ return defaultValue; }
        return Boolean.valueOf(value.trim());
    }
}