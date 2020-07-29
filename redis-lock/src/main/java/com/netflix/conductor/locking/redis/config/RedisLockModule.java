/*
 * Copyright (c) 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.locking.redis.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.RedisLock;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class RedisLockModule extends AbstractModule{

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLockModule.class);

    @Override
    protected void configure() {
        bind(RedisLockConfiguration.class).to(SystemPropertiesRedisLockConfiguration.class);
        bind(Lock.class).to(RedisLock.class).in(Singleton.class);
    }

    @Provides
    @javax.inject.Singleton
    public Redisson getRedissonBasedOnConfig(RedisLockConfiguration clusterConfiguration) {
        RedisLockConfiguration.REDIS_SERVER_TYPE redisServerType;
        try {
            redisServerType = clusterConfiguration.getRedisServerType();
        } catch (IllegalArgumentException ie) {
            final String message = "Invalid Redis server type: " + clusterConfiguration.getRedisServerType()
                    + ", supported values are: " + Arrays.toString(clusterConfiguration.getRedisServerType().values());
            LOGGER.error(message);
            throw new ProvisionException(message, ie);
        }
        String redisServerAddress = clusterConfiguration.getRedisServerAddress();
        String redisServerPassword = clusterConfiguration.getRedisServerPassword();
        String masterName = clusterConfiguration.getRedisServerMasterName();

        Config redisConfig = new Config();

        int connectionTimeout = 10000;
        switch (redisServerType) {
            case SINGLE:
                redisConfig.useSingleServer()
                        .setAddress(redisServerAddress)
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
            case CLUSTER:
                redisConfig.useClusterServers()
                        .setScanInterval(2000) // cluster state scan interval in milliseconds
                        .addNodeAddress(redisServerAddress.split(","))
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
            case SENTINEL:
                redisConfig.useSentinelServers()
                        .setScanInterval(2000)
                        .setMasterName(masterName)
                        .addSentinelAddress(redisServerAddress)
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
        }

        return (Redisson) Redisson.create(redisConfig);
    }
}
