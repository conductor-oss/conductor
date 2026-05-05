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
package io.orkes.conductor.scheduler.config;

import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration smoke tests for the scheduler persistence modules.
 *
 * <p>Uses {@link ApplicationContextRunner} to verify that the {@code @ConditionalOnExpression}
 * guards on each persistence module's configuration class work correctly.
 */
public abstract class AbstractSchedulerAutoConfigurationSmokeTest {

    protected abstract String dbTypeValue();

    protected abstract String datasourceUrl();

    protected abstract String driverClassName();

    protected abstract Class<?> persistenceAutoConfigClass();

    protected abstract Class<? extends SchedulerDAO> expectedDaoClass();

    @Configuration
    static class SharedTestBeans {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }

        @Bean
        public RetryTemplate retryTemplate() {
            return new RetryTemplate();
        }
    }

    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                DataSourceAutoConfiguration.class, persistenceAutoConfigClass()))
                .withUserConfiguration(SharedTestBeans.class)
                .withPropertyValues(
                        "spring.datasource.url=" + datasourceUrl(),
                        "spring.datasource.driver-class-name=" + driverClassName(),
                        "spring.flyway.enabled=false");
    }

    @Test
    public void testSchedulerDAO_registeredWhenBothPropertiesSet() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=" + dbTypeValue(), "conductor.scheduler.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(SchedulerDAO.class);
                            assertThat(ctx.getBean(SchedulerDAO.class))
                                    .isInstanceOf(expectedDaoClass());
                        });
    }

    @Test
    public void testNoBeansRegistered_whenSchedulerEnabledAbsent() {
        baseRunner()
                .withPropertyValues("conductor.db.type=" + dbTypeValue())
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    @Test
    public void testNoBeansRegistered_whenSchedulerEnabledFalse() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=" + dbTypeValue(), "conductor.scheduler.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    @Test
    public void testNoSchedulerDAO_whenDbTypeAbsent() {
        baseRunner()
                .withPropertyValues("conductor.scheduler.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    @Test
    public void testNoSchedulerDAO_whenDbTypeIsWrongBackend() {
        String wrongType =
                dbTypeValue().equals("postgres")
                        ? "mysql"
                        : dbTypeValue().equals("mysql") ? "sqlite" : "postgres";
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=" + wrongType, "conductor.scheduler.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }
}
