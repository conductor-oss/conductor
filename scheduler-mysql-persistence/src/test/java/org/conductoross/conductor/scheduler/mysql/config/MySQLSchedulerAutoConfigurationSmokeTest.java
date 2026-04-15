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
package org.conductoross.conductor.scheduler.mysql.config;

import org.conductoross.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.mysql.dao.MySQLSchedulerDAO;

/**
 * Smoke-tests {@link MySQLSchedulerConfiguration} auto-configuration conditions.
 *
 * <p>Positive path uses a Testcontainers MySQL instance (the {@code jdbc:tc:…} URL spins up a
 * container on first use and reuses it within the JVM). Negative paths run without any DB.
 */
public class MySQLSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected String dbTypeValue() {
        return "mysql";
    }

    @Override
    protected String datasourceUrl() {
        return "jdbc:tc:mysql:8.0:///scheduler_smoke_test";
    }

    @Override
    protected String driverClassName() {
        return "org.testcontainers.jdbc.ContainerDatabaseDriver";
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return MySQLSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return MySQLSchedulerDAO.class;
    }
}
