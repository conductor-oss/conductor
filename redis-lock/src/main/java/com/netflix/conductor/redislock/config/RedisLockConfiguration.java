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
package com.netflix.conductor.redislock.config;

import java.util.Arrays;

import org.redisson.Redisson;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.redislock.config.RedisLockProperties.REDIS_SERVER_TYPE;
import com.netflix.conductor.redislock.lock.RedisLock;

@Configuration
@EnableConfigurationProperties(RedisLockProperties.class)
@ConditionalOnProperty(name = "conductor.workflow-execution-lock.type", havingValue = "redis")
public class RedisLockConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLockConfiguration.class);

    @Bean
    public Redisson getRedisson(RedisLockProperties properties) {
        RedisLockProperties.REDIS_SERVER_TYPE redisServerType;
        try {
            redisServerType = properties.getServerType();
        } catch (IllegalArgumentException ie) {
            final String message =
                    "Invalid Redis server type: "
                            + properties.getServerType()
                            + ", supported values are: "
                            + Arrays.toString(REDIS_SERVER_TYPE.values());
            LOGGER.error(message);
            throw new RuntimeException(message, ie);
        }
        String redisServerAddress = properties.getServerAddress();
        String redisServerPassword = properties.getServerPassword();
        String masterName = properties.getServerMasterName();

        Config redisConfig = new Config();

        int connectionTimeout = 10000;
        switch (redisServerType) {
            case SINGLE:
                redisConfig
                        .useSingleServer()
                        .setAddress(redisServerAddress)
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
            case CLUSTER:
                redisConfig
                        .useClusterServers()
                        .setScanInterval(2000) // cluster state scan interval in milliseconds
                        .addNodeAddress(redisServerAddress.split(","))
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
            case SENTINEL:
                redisConfig
                        .useSentinelServers()
                        .setScanInterval(2000)
                        .setMasterName(masterName)
                        .addSentinelAddress(redisServerAddress)
                        .setPassword(redisServerPassword)
                        .setTimeout(connectionTimeout);
                break;
        }

        return (Redisson) Redisson.create(redisConfig);
    }

    @Bean
    public Lock provideLock(Redisson redisson, RedisLockProperties properties) {
        return new RedisLock(redisson, properties);
    }
}
