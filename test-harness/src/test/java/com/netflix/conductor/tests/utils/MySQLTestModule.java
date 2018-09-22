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
import com.netflix.conductor.dao.mysql.EmbeddedDatabase;
import com.netflix.conductor.dao.mysql.MySQLBaseDAOTest;
import com.netflix.conductor.dao.mysql.MySQLExecutionDAO;
import com.netflix.conductor.dao.mysql.MySQLMetadataDAO;
import com.netflix.conductor.dao.mysql.MySQLQueueDAO;
import com.netflix.conductor.server.ConductorConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * @author mustafa
 */
public class MySQLTestModule extends AbstractModule {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private int maxThreads = 50;

    private ExecutorService executorService;

    protected final EmbeddedDatabase DB = EmbeddedDatabase.INSTANCE;

    @Provides
    @Singleton
    public DataSource getDataSource(Configuration config) {

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getProperty("jdbc.url", "jdbc:mysql://localhost:33307/conductor"));
        hikariConfig.setUsername(config.getProperty("jdbc.username", "conductor"));
        hikariConfig.setPassword(config.getProperty("jdbc.password", "password"));

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");

        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setMinimumIdle(20);

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        if (!EmbeddedDatabase.hasBeenMigrated()) {
            synchronized (EmbeddedDatabase.class) {
                flywayMigrate(dataSource);
                EmbeddedDatabase.setHasBeenMigrated();
            }
        }

        return dataSource;
    }

    @Override
    protected void configure() {

        configureExecutorService();
        ConductorConfig config = new ConductorConfig();
        bind(Configuration.class).toInstance(config);

        bind(MetadataDAO.class).to(MySQLMetadataDAO.class).asEagerSingleton();
        bind(ExecutionDAO.class).to(MySQLExecutionDAO.class).asEagerSingleton();
        bind(QueueDAO.class).to(MySQLQueueDAO.class).asEagerSingleton();
        bind(IndexDAO.class).to(MockIndexDAO.class);
        install(new CoreModule());
        bind(UserTask.class).asEagerSingleton();
        bind(ExternalPayloadStorage.class).to(MockExternalPayloadStorage.class);
    }

    private synchronized static void flywayMigrate(DataSource dataSource) {
        if (EmbeddedDatabase.hasBeenMigrated()) {
            return;
        }

        synchronized (MySQLBaseDAOTest.class) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setBaselineOnMigrate(true);
            flyway.setPlaceholderReplacement(false);
            flyway.clean();
            flyway.migrate();
        }
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
