/*
 * Copyright 2019 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.ProvisionException;
import com.google.inject.util.Modules;
import com.netflix.conductor.cassandra.CassandraModule;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.contribs.http.HttpTask;
import com.netflix.conductor.contribs.http.RestClientManager;
import com.netflix.conductor.contribs.json.JsonJqTransform;
import com.netflix.conductor.contribs.kafka.KafkaProducerManager;
import com.netflix.conductor.contribs.kafka.KafkaPublishTask;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.JacksonModule;
import com.netflix.conductor.core.utils.LocalOnlyLockModule;
import com.netflix.conductor.core.utils.NoopLockModule;
import com.netflix.conductor.core.execution.WorkflowExecutorModule;
import com.netflix.conductor.core.utils.DummyPayloadStorage;
import com.netflix.conductor.core.utils.S3PayloadStorage;
import com.netflix.conductor.noopindex.NoopIndexModule;
import com.netflix.conductor.dao.RedisWorkflowModule;
import com.netflix.conductor.elasticsearch.ElasticSearchModule;
import com.netflix.conductor.locking.redis.config.RedisLockModule;
import com.netflix.conductor.mysql.MySQLWorkflowModule;
import com.netflix.conductor.server.DynomiteClusterModule;
import com.netflix.conductor.server.JerseyModule;
import com.netflix.conductor.server.LocalRedisModule;
import com.netflix.conductor.server.RedisClusterModule;
import com.netflix.conductor.server.RedisSentinelModule;
import com.netflix.conductor.server.ServerModule;
import com.netflix.conductor.server.SwaggerModule;
import com.netflix.conductor.zookeeper.config.ZookeeperModule;
import com.netflix.conductor.postgres.PostgresWorkflowModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;

// TODO Investigate whether this should really be a ThrowingProvider.
public class ModulesProvider implements Provider<List<AbstractModule>> {
    private static final Logger logger = LoggerFactory.getLogger(ModulesProvider.class);

    private final Configuration configuration;

    enum ExternalPayloadStorageType {
        S3,
        DUMMY
    }

    @Inject
    public ModulesProvider(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<AbstractModule> get() {
        AbstractModule resolvedModule = (AbstractModule) Modules.override(selectModulesToLoad()).with(configuration.getAdditionalModules());
        return singletonList(resolvedModule);
    }

    private List<AbstractModule> selectModulesToLoad() {
        Configuration.DB database;
        List<AbstractModule> modules = new ArrayList<>();

        // Load Jackson module early to make ObjectMapper provider available across all the usages.
        modules.add(new JacksonModule());

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
                logger.info("Starting conductor server using MySQL data store.");
                break;
            case POSTGRES:
                modules.add(new PostgresWorkflowModule());
                logger.info("Starting conductor server using Postgres data store.");
                break;
            case MEMORY:
                modules.add(new LocalRedisModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using in memory data store.");
                break;
            case REDIS_CLUSTER:
                modules.add(new RedisClusterModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using redis_cluster.");
                break;
            case CASSANDRA:
                modules.add(new CassandraModule());
                logger.info("Starting conductor server using cassandra.");
                break;
            case REDIS_SENTINEL:
                modules.add(new RedisSentinelModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using redis_sentinel.");
                break;
        }

        if (configuration.isIndexingPersistenceEnabled())
            modules.add(new ElasticSearchModule());
        else
            modules.add(new NoopIndexModule());

        modules.add(new WorkflowExecutorModule());

        if (configuration.getJerseyEnabled()) {
            modules.add(new JerseyModule());
            modules.add(new SwaggerModule());
        }

        if (configuration.enableWorkflowExecutionLock()) {
            Configuration.LOCKING_SERVER lockingServer;
            try {
                lockingServer = configuration.getLockingServer();
            } catch (IllegalArgumentException ie) {
                final String message = "Invalid locking server name: " + configuration.getLockingServerString()
                        + ", supported values are: " + Arrays.toString(Configuration.LOCKING_SERVER.values());
                logger.error(message);
                throw new ProvisionException(message, ie);
            }

            switch (lockingServer) {
                case REDIS:
                    modules.add(new RedisLockModule());
                    logger.info("Starting locking module using Redis cluster.");
                    break;
                case ZOOKEEPER:
                    modules.add(new ZookeeperModule());
                    logger.info("Starting locking module using Zookeeper cluster.");
                    break;
                case LOCAL_ONLY:
                    modules.add(new LocalOnlyLockModule());
                    logger.info("Starting locking module using local only JVM locking.");
                    break;
                default:
                    break;
            }
        } else {
            modules.add(new NoopLockModule());
            logger.warn("Starting locking module using Noop Lock.");
        }

        ExternalPayloadStorageType externalPayloadStorageType = null;
        String externalPayloadStorageString = configuration.getProperty("workflow.external.payload.storage", "DUMMY");
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

        new HttpTask(new RestClientManager(configuration), configuration, new JsonMapperProvider().get());
        new KafkaPublishTask(configuration, new KafkaProducerManager(configuration), new JsonMapperProvider().get());
        new JsonJqTransform(new JsonMapperProvider().get());
        modules.add(new ServerModule());

        return modules;
    }
}
