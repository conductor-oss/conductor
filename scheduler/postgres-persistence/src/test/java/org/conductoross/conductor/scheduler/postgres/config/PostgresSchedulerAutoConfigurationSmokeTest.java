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
package org.conductoross.conductor.scheduler.postgres.config;

import org.conductoross.conductor.scheduler.postgres.dao.PostgresSchedulerDAO;

import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;

/**
 * Smoke-tests {@link PostgresSchedulerConfiguration} auto-configuration conditions.
 *
 * <p>Positive path uses a Testcontainers PostgreSQL instance (the {@code jdbc:tc:…} URL spins up a
 * container on first use and reuses it within the JVM). Negative paths run without any DB.
 */
public class PostgresSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected String dbTypeValue() {
        return "postgres";
    }

    @Override
    protected String datasourceUrl() {
        return "jdbc:tc:postgresql:15-alpine:///scheduler_smoke_test";
    }

    @Override
    protected String driverClassName() {
        return "org.testcontainers.jdbc.ContainerDatabaseDriver";
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return PostgresSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return PostgresSchedulerDAO.class;
    }
}
