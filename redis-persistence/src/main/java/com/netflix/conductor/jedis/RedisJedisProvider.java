/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.conductor.jedis;

import com.google.common.collect.Lists;
import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.commands.JedisCommands;

public class RedisJedisProvider implements Provider<JedisCommands> {
    private static Logger logger = LoggerFactory.getLogger(RedisJedisProvider.class);

    private final HostSupplier hostSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public RedisJedisProvider(HostSupplier hostSupplier, DynomiteConfiguration configuration) {
        this.hostSupplier = hostSupplier;
        this.configuration = configuration;
    }

    @Override
    public JedisCommands get() {
        // FIXME Do we really want to ignore all additional hosts?
        Host host = Lists.newArrayList(hostSupplier.getHosts()).get(0);

        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(1000);
        logger.info("Starting conductor server using redis_cluster " + configuration.getClusterName());

        JedisPool jedisPool = new JedisPool(poolConfig, host.getHostName(), host.getPort());
        return new JedisCluster(jedisPool);
    }
}
