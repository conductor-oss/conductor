package com.netflix.conductor.tests.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.CoreModule;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.mysql.MySQLExecutionDAO;
import com.netflix.conductor.dao.mysql.MySQLMetadataDAO;
import com.netflix.conductor.dao.mysql.MySQLQueueDAO;
import com.netflix.conductor.server.ConductorConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * @author mustafa
 */
public class MySQLTestModule extends AbstractModule {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private int maxThreads = 50;

    private ExecutorService executorService;

    @Provides
    @Singleton
    public DataSource getDataSource(Configuration config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getProperty("jdbc.url", "jdbc:mysql://localhost:3306/conductor?useSSL=false&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC"));
        dataSource.setUsername(config.getProperty("jdbc.username", "root"));
        dataSource.setPassword(config.getProperty("jdbc.password", "test123"));
        dataSource.setAutoCommit(false);

        dataSource.setMaximumPoolSize(config.getIntProperty("jdbc.maxPoolSize", 100));
        dataSource.setMinimumIdle(config.getIntProperty("jdbc.minIdleSize", 20));
        dataSource.setIdleTimeout(config.getIntProperty("jdbc.idleTimeout", 1000 * 300));
        dataSource.setTransactionIsolation(config.getProperty("jdbc.isolationLevel", "TRANSACTION_REPEATABLE_READ"));

        flywayMigrate(config, dataSource);

        return dataSource;
    }

    @Override
    protected void configure() {
        
        configureExecutorService();
        ConductorConfig config = new ConductorConfig();
        bind(Configuration.class).toInstance(config);
        
        bind(MetadataDAO.class).to(MySQLMetadataDAO.class);
        bind(ExecutionDAO.class).to(MySQLExecutionDAO.class);
        bind(QueueDAO.class).to(MySQLQueueDAO.class);
        bind(IndexDAO.class).to(MockIndexDAO.class);
        install(new CoreModule());
        bind(UserTask.class).asEagerSingleton();
        bind(ExternalPayloadStorage.class).to(MockExternalPayloadStorage.class);
    }

    private void flywayMigrate(Configuration config, DataSource dataSource) {
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setPlaceholderReplacement(false);
        flyway.setBaselineOnMigrate(true);
        flyway.clean();
        flyway.migrate();
    }

    
    @Provides
    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    private void configureExecutorService() {
        AtomicInteger count = new AtomicInteger(0);
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, runnable -> {
            Thread workflowWorkerThread = new Thread(runnable);
            workflowWorkerThread.setName(String.format("workflow-worker-%d", count.getAndIncrement()));
            return workflowWorkerThread;
        });
    }
}
