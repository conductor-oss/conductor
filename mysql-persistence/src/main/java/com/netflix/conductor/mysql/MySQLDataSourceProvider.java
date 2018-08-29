package com.netflix.conductor.mysql;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

public class MySQLDataSourceProvider implements Provider<DataSource> {
    private static final Logger logger = LoggerFactory.getLogger(MySQLDataSourceProvider.class);

    private final MySQLConfiguration configuration;

    @Inject
    public MySQLDataSourceProvider(MySQLConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public DataSource get() {
        HikariDataSource dataSource = new HikariDataSource(createConfiguration());
        dataSource.setJdbcUrl(configuration.getJdbcUrl());
        dataSource.setUsername(configuration.getJdbcUserName());
        dataSource.setPassword(configuration.getJdbcPassword());
        dataSource.setAutoCommit(false);
        flywayMigrate(dataSource);

        return dataSource;
    }

    private HikariConfig createConfiguration(){
        HikariConfig cfg = new HikariConfig();
        cfg.setMaxLifetime(configuration.getConnectionMaxLifetime());
        cfg.setIdleTimeout(configuration.getConnectionIdleTimeout());
        cfg.setConnectionTimeout(configuration.getConnectionTimeout());
        cfg.setAutoCommit(configuration.isAutoCommit());

        ThreadFactory tf = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("hikari-mysql-%d")
                .build();

        cfg.setThreadFactory(tf);
        return cfg;
    }
    // TODO Move this into a class that has complete lifecycle for the connection, i.e. startup and shutdown.
    private void flywayMigrate(DataSource dataSource) {
        boolean enabled = configuration.isFlywayEnabled();
        if (!enabled) {
            logger.debug("Flyway migrations are disabled");
            return;
        }


        Flyway flyway = new Flyway();
        configuration.getFlywayTable().ifPresent(tableName -> {
            logger.debug("Using Flyway migration table '{}'", tableName);
            flyway.setTable(tableName);
        });

        flyway.setDataSource(dataSource);
        flyway.setPlaceholderReplacement(false);
        flyway.migrate();
    }
}
