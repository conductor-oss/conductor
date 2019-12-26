/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.postgres;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.dao.postgres.PostgresExecutionDAO;
import com.netflix.conductor.dao.postgres.PostgresMetadataDAO;
import com.netflix.conductor.dao.postgres.PostgresQueueDAO;

import javax.sql.DataSource;

/**
 * @author mustafa
 */
public class PostgresWorkflowModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(PostgresConfiguration.class).to(SystemPropertiesPostgresConfiguration.class);
        bind(DataSource.class).toProvider(PostgresDataSourceProvider.class).in(Scopes.SINGLETON);
        bind(MetadataDAO.class).to(PostgresMetadataDAO.class);
        bind(EventHandlerDAO.class).to(PostgresMetadataDAO.class);
        bind(ExecutionDAO.class).to(PostgresExecutionDAO.class);
        bind(RateLimitingDAO.class).to(PostgresExecutionDAO.class);
        bind(PollDataDAO.class).to(PostgresExecutionDAO.class);
        bind(QueueDAO.class).to(PostgresQueueDAO.class);
    }
}
