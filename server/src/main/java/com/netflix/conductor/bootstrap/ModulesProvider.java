package com.netflix.conductor.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.ProvisionException;
import com.netflix.conductor.cassandra.CassandraModule;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.contribs.http.HttpTask;
import com.netflix.conductor.contribs.http.RestClientManager;
import com.netflix.conductor.contribs.json.JsonJqTransform;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutorModule;
import com.netflix.conductor.core.utils.DummyPayloadStorage;
import com.netflix.conductor.core.utils.S3PayloadStorage;
import com.netflix.conductor.dao.RedisWorkflowModule;
import com.netflix.conductor.elasticsearch.ElasticSearchModule;
import com.netflix.conductor.mysql.MySQLWorkflowModule;
import com.netflix.conductor.server.DynomiteClusterModule;
import com.netflix.conductor.server.JerseyModule;
import com.netflix.conductor.server.LocalRedisModule;
import com.netflix.conductor.server.RedisClusterModule;
import com.netflix.conductor.server.RedisSentinelModule;
import com.netflix.conductor.server.ServerModule;
import com.netflix.conductor.server.SwaggerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO Investigate whether this should really be a ThrowingProvider.
public class ModulesProvider implements Provider<List<AbstractModule>> {
    private static final Logger logger = LoggerFactory.getLogger(ModulesProvider.class);

    private final Configuration configuration;

    enum ExternalPayloadStorageType {
        S3
    }

    @Inject
    public ModulesProvider(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<AbstractModule> get() {
        List<AbstractModule> modulesToLoad = new ArrayList<>();

        modulesToLoad.addAll(selectModulesToLoad());
        modulesToLoad.addAll(configuration.getAdditionalModules());

        return modulesToLoad;
    }

    private List<AbstractModule> selectModulesToLoad() {
        Configuration.DB database;
        List<AbstractModule> modules = new ArrayList<>();

        try {
            database = configuration.getDB();
        } catch (IllegalArgumentException ie) {
            final String message = "Invalid db name: " + configuration.getDBString()
                    + ", supported values are: " + Arrays.toString(Configuration.DB.values());
            logger.error(message);
            throw new ProvisionException(message, ie);
        }

        switch (database) {
            case REDIS:
            case DYNOMITE:
                modules.add(new DynomiteClusterModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using dynomite/redis cluster.");
                break;

            case MYSQL:
                modules.add(new MySQLWorkflowModule());
                logger.info("Starting conductor server using MySQL data store", database);
                break;
            case MEMORY:
                modules.add(new LocalRedisModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using in memory data store");
                break;
            case REDIS_CLUSTER:
                modules.add(new RedisClusterModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using redis_cluster.");
                break;
            case CASSANDRA:
                modules.add(new CassandraModule());
                logger.info("Starting conductor server using cassandra.");
            case REDIS_SENTINEL:
                modules.add(new RedisSentinelModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using redis_sentinel.");
                break;
        }

        modules.add(new ElasticSearchModule());

        modules.add(new WorkflowExecutorModule());

        if (configuration.getJerseyEnabled()) {
            modules.add(new JerseyModule());
            modules.add(new SwaggerModule());
        }

        ExternalPayloadStorageType externalPayloadStorageType = null;
        String externalPayloadStorageString = configuration.getProperty("workflow.external.payload.storage", "");
        try {
            externalPayloadStorageType = ExternalPayloadStorageType.valueOf(externalPayloadStorageString);
        } catch (IllegalArgumentException e) {
            logger.info("External payload storage is not configured, provided: {}, supported values are: {}", externalPayloadStorageString, Arrays.toString(ExternalPayloadStorageType.values()), e);
        }

        if (externalPayloadStorageType == ExternalPayloadStorageType.S3) {
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ExternalPayloadStorage.class).to(S3PayloadStorage.class);
                }
            });
        } else {
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ExternalPayloadStorage.class).to(DummyPayloadStorage.class);
                }
            });
        }

        new HttpTask(new RestClientManager(), configuration);
        new JsonJqTransform();
        modules.add(new ServerModule());

        return modules;
    }
}
