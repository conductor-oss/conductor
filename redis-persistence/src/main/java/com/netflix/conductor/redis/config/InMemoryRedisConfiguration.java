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
package com.netflix.conductor.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.redis.dynoqueue.LocalhostHostSupplier;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.dyno.connectionpool.HostSupplier;

import static com.netflix.conductor.redis.config.RedisCommonConfiguration.DEFAULT_CLIENT_INJECTION_NAME;
import static com.netflix.conductor.redis.config.RedisCommonConfiguration.READ_CLIENT_INJECTION_NAME;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "memory")
public class InMemoryRedisConfiguration {

    @Bean
    public HostSupplier hostSupplier(RedisProperties properties) {
        return new LocalhostHostSupplier(properties);
    }

    @Bean(name = {DEFAULT_CLIENT_INJECTION_NAME, READ_CLIENT_INJECTION_NAME})
    public JedisMock jedisMock() {
        return new JedisMock();
    }
}
