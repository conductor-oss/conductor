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
package org.conductoross.conductor.scheduler.config;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration smoke tests for the scheduler persistence modules.
 *
 * <p>Uses {@link ApplicationContextRunner} to verify that the {@code @ConditionalOnExpression}
 * guards on each persistence module's configuration class work correctly: the right beans appear
 * when both required properties are set, and no beans appear when either property is absent or
 * wrong.
 *
 * <p>These tests catch bugs that the DAO-level and service-level integration tests cannot, because
 * those tests bypass auto-configuration and wire beans manually:
 *
 * <ul>
 *   <li>Typos in the {@code @ConditionalOnExpression} string (e.g. {@code 'postgresql'} instead of
 *       {@code 'postgres'})
 *   <li>A missing or wrong entry in {@code META-INF/spring/...AutoConfiguration.imports}
 * </ul>
 *
 * <p>Note: {@link SchedulerService} and {@link SchedulerResource} wiring via {@link
 * WorkflowSchedulerConfiguration} is not tested here because {@code @ConditionalOnBean} ordering is
 * non-trivial with {@code ApplicationContextRunner} and that wiring is already covered end-to-end
 * by {@code AbstractSchedulerServiceIntegrationTest}.
 *
 * <p>Subclasses supply:
 *
 * <ul>
 *   <li>{@link #dbTypeValue()} — the exact string to use for {@code conductor.db.type}
 *   <li>{@link #datasourceUrl()} — a JDBC URL suitable for the backend (in-memory for SQLite;
 *       Testcontainers {@code jdbc:tc:…} for Postgres/MySQL)
 *   <li>{@link #driverClassName()} — the JDBC driver class name
 *   <li>{@link #persistenceAutoConfigClass()} — the {@code @AutoConfiguration} class under test
 *   <li>{@link #expectedDaoClass()} — the concrete {@link SchedulerDAO} subclass that should be
 *       registered
 * </ul>
 */
public abstract class AbstractSchedulerAutoConfigurationSmokeTest {

    /** The value to set for {@code conductor.db.type}. */
    protected abstract String dbTypeValue();

    /** A JDBC URL that can reach the target database in a test context. */
    protected abstract String datasourceUrl();

    /** The JDBC driver class name. */
    protected abstract String driverClassName();

    /** The {@code @AutoConfiguration} class that should register the {@link SchedulerDAO} bean. */
    protected abstract Class<?> persistenceAutoConfigClass();

    /** The concrete {@link SchedulerDAO} subclass expected to be registered. */
    protected abstract Class<? extends SchedulerDAO> expectedDaoClass();

    // -------------------------------------------------------------------------
    // Shared infrastructure
    // -------------------------------------------------------------------------

    /** Provides ObjectMapper for all context runs. */
    @Configuration
    static class SharedTestBeans {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }
    }

    protected ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                DataSourceAutoConfiguration.class, persistenceAutoConfigClass()))
                .withUserConfiguration(SharedTestBeans.class)
                .withPropertyValues(
                        "spring.datasource.url=" + datasourceUrl(),
                        "spring.datasource.driver-class-name=" + driverClassName(),
                        // Disable Spring Boot's Flyway auto-config; each persistence module runs
                        // its own Flyway bean
                        "spring.flyway.enabled=false");
    }

    // -------------------------------------------------------------------------
    // Positive cases
    // -------------------------------------------------------------------------

    /**
     * When both {@code conductor.db.type} and {@code conductor.scheduler.enabled=true} are set, the
     * persistence auto-configuration registers exactly one {@link SchedulerDAO} of the expected
     * concrete type.
     */
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

    // -------------------------------------------------------------------------
    // Negative cases — missing / wrong properties
    // -------------------------------------------------------------------------

    /**
     * When {@code conductor.scheduler.enabled} is absent, no {@link SchedulerDAO} should be
     * registered.
     */
    @Test
    public void testNoBeansRegistered_whenSchedulerEnabledAbsent() {
        baseRunner()
                .withPropertyValues("conductor.db.type=" + dbTypeValue())
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    /**
     * When {@code conductor.scheduler.enabled=false}, no {@link SchedulerDAO} should be registered.
     */
    @Test
    public void testNoBeansRegistered_whenSchedulerEnabledFalse() {
        baseRunner()
                .withPropertyValues(
                        "conductor.db.type=" + dbTypeValue(), "conductor.scheduler.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    /**
     * When {@code conductor.db.type} is absent, no {@link SchedulerDAO} should be registered even
     * when the scheduler is enabled.
     */
    @Test
    public void testNoSchedulerDAO_whenDbTypeAbsent() {
        baseRunner()
                .withPropertyValues("conductor.scheduler.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulerDAO.class));
    }

    /**
     * When {@code conductor.db.type} is set to a different backend (e.g. another module's type),
     * this module's {@link SchedulerDAO} must not be registered.
     */
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
