/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.redis.jedis;

import com.netflix.conductor.redis.config.utils.RedisProperties;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.commands.JedisCommands;

import javax.inject.Provider;

@SuppressWarnings("rawtypes")
public class RedisClusterJedisProvider implements Provider<JedisCommands> {

    private final JedisCluster jedisCluster;

    public RedisClusterJedisProvider(HostSupplier hostSupplier, RedisProperties properties) {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setMaxTotal(properties.getMaxConnectionsPerHost());
        Host host = hostSupplier.getHosts().get(0);
        this.jedisCluster = new JedisCluster(
            new redis.clients.jedis.JedisCluster(new HostAndPort(host.getHostName(), host.getPort()),
                genericObjectPoolConfig));
    }

    @Override
    public JedisCommands get() {
        return jedisCluster;
    }
}
