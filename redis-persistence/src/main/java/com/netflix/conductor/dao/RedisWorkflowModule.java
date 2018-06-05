/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao;
/**
 *
 */

import com.google.inject.AbstractModule;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.dynomite.DynoProxy;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.redis.DynoShardSupplier;

import javax.inject.Inject;

import redis.clients.jedis.JedisCommands;

/**
 * @author Viren
 */
public class RedisWorkflowModule extends AbstractModule {
    private final Configuration config;
    private final JedisCommands dynomiteConnection;
    private final HostSupplier hostSupplier;

    @Inject
    public RedisWorkflowModule(Configuration config, JedisCommands dynomiteConnection, HostSupplier hostSupplier) {
        this.config = config;
        this.dynomiteConnection = dynomiteConnection;
        this.hostSupplier = hostSupplier;
    }

    @Override
    protected void configure() {

        bind(MetadataDAO.class).to(RedisMetadataDAO.class);
        bind(ExecutionDAO.class).to(RedisExecutionDAO.class);
        bind(QueueDAO.class).to(DynoQueueDAO.class);

        bind(DynoQueueDAO.class).toInstance(createQueueDAO());
        bind(DynoProxy.class).toInstance(new DynoProxy(dynomiteConnection));

    }

    private DynoQueueDAO createQueueDAO() {

        String localDC = config.getAvailabilityZone();
        localDC = localDC.replaceAll(config.getRegion(), "");
        DynoShardSupplier ss = new DynoShardSupplier(hostSupplier, config.getRegion(), localDC);

        return new DynoQueueDAO(dynomiteConnection, dynomiteConnection, ss, config);
    }
}
