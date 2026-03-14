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
package org.conductoross.conductor.scheduler.sqlite.config;

import org.conductoross.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;

/**
 * Smoke-tests {@link SqliteSchedulerConfiguration} auto-configuration conditions.
 *
 * <p>Positive path uses an in-memory SQLite database — no Docker required. This makes the SQLite
 * smoke tests the fastest way to verify the auto-configuration condition logic locally.
 */
public class SqliteSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected String dbTypeValue() {
        return "sqlite";
    }

    @Override
    protected String datasourceUrl() {
        return "jdbc:sqlite::memory:";
    }

    @Override
    protected String driverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return SqliteSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return SqliteSchedulerDAO.class;
    }
}
