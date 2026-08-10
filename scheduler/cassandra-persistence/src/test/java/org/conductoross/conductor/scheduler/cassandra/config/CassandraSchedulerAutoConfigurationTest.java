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
package org.conductoross.conductor.scheduler.cassandra.config;

import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CassandraSchedulerAutoConfigurationTest {

    @Configuration
    static class MockCassandraBeans {
        @Bean
        public Session cassandraSession() {
            return mock(Session.class);
        }

        @Bean
        public CassandraProperties cassandraProperties() {
            CassandraProperties props = new CassandraProperties();
            props.setKeyspace("test_keyspace");
            return props;
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }
    }

    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CassandraSchedulerConfiguration.class))
                .withUserConfiguration(MockCassandraBeans.class);
    }

    @Test
    public void testBeansRegistered_whenCassandraAndSchedulerEnabled() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=cassandra", "conductor.scheduler.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(SchedulerDAO.class);
                            assertThat(ctx).hasSingleBean(SchedulerArchivalDAO.class);
                        });
    }

    @Test
    public void testNoBeansRegistered_whenSchedulerEnabledMissing() {
        baseRunner()
                .withPropertyValues("conductor.db.type=cassandra")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(SchedulerDAO.class);
                            assertThat(ctx).doesNotHaveBean(SchedulerArchivalDAO.class);
                        });
    }

    @Test
    public void testNoBeansRegistered_whenSchedulerDisabled() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=cassandra", "conductor.scheduler.enabled=false")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(SchedulerDAO.class);
                            assertThat(ctx).doesNotHaveBean(SchedulerArchivalDAO.class);
                        });
    }

    @Test
    public void testNoBeansRegistered_whenDbTypeIsNotCassandra() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=postgres", "conductor.scheduler.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(SchedulerDAO.class);
                            assertThat(ctx).doesNotHaveBean(SchedulerArchivalDAO.class);
                        });
    }

    @Test
    public void testNoBeansRegistered_whenDbTypeAbsent() {
        baseRunner()
                .withPropertyValues("conductor.scheduler.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(SchedulerDAO.class);
                            assertThat(ctx).doesNotHaveBean(SchedulerArchivalDAO.class);
                        });
    }
}
