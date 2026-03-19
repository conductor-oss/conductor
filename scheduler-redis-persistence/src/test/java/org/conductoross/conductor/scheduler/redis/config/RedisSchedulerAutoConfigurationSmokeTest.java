/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.scheduler.redis.config;

import org.conductoross.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.redis.dao.RedisSchedulerDAO;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

public class RedisSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected ApplicationContextRunner baseRunner() {
        JedisProxy mockProxy = mock(JedisProxy.class);
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(persistenceAutoConfigClass()))
                .withBean(JedisProxy.class, () -> mockProxy)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withPropertyValues("spring.flyway.enabled=false");
    }

    @Override
    protected String dbTypeValue() {
        return "redis_standalone";
    }

    @Override
    protected String datasourceUrl() {
        return ""; // not used — Redis does not need a JDBC datasource
    }

    @Override
    protected String driverClassName() {
        return ""; // not used
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return RedisSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return RedisSchedulerDAO.class;
    }
}
