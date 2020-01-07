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

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.commands.JedisCommands;

public class RedisSentinelJedisProvider implements Provider<JedisCommands> {

    private static Logger logger = LoggerFactory.getLogger(RedisSentinelJedisProvider.class);
    private final JedisSentinel jedisSentinel;

    @Inject
    public RedisSentinelJedisProvider(HostSupplier hostSupplier, DynomiteConfiguration configuration) {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setMinIdle(5);
        genericObjectPoolConfig.setMaxTotal(configuration.getMaxConnectionsPerHost());
        logger.info("Starting conductor server using redis_sentinel and cluster " + configuration.getClusterName());
        Set<String> sentinels = new HashSet<>();
        for (Host host : hostSupplier.getHosts()) {
            sentinels.add(host.getHostName() + ":" + host.getPort());
        }
        this.jedisSentinel = new JedisSentinel(new JedisSentinelPool(configuration.getClusterName(), sentinels, genericObjectPoolConfig));
    }

    @Override
    public JedisCommands get() {
        return jedisSentinel;
    }
}
