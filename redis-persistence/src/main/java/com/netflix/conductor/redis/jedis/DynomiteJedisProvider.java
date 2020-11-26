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
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.jedis.DynoJedisClient;
import redis.clients.jedis.commands.JedisCommands;

import javax.inject.Provider;

public class DynomiteJedisProvider implements Provider<JedisCommands> {

    private final HostSupplier hostSupplier;
    private final TokenMapSupplier tokenMapSupplier;
    private final RedisProperties properties;

    public DynomiteJedisProvider(RedisProperties properties, HostSupplier hostSupplier,
        TokenMapSupplier tokenMapSupplier) {
        this.properties = properties;
        this.hostSupplier = hostSupplier;
        this.tokenMapSupplier = tokenMapSupplier;
    }

    @Override
    public JedisCommands get() {
        ConnectionPoolConfigurationImpl connectionPoolConfiguration =
            new ConnectionPoolConfigurationImpl(properties.getClusterName())
                .withTokenSupplier(tokenMapSupplier)
                .setLocalRack(properties.getAvailabilityZone())
                .setLocalDataCenter(properties.getRegion())
                .setSocketTimeout(0)
                .setConnectTimeout(0)
                .setMaxConnsPerHost(
                    properties.getMaxConnectionsPerHost()
                );

        return new DynoJedisClient.Builder()
            .withHostSupplier(hostSupplier)
            .withApplicationName(properties.getAppId())
            .withDynomiteClusterName(properties.getClusterName())
            .withCPConfig(connectionPoolConfiguration)
            .build();
    }
}
