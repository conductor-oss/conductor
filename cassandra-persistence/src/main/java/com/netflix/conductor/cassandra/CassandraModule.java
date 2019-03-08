/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.netflix.conductor.dao.cassandra.CassandraExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.util.Statements;

public class CassandraModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraConfiguration.class).to(SystemPropertiesCassandraConfiguration.class);
        bind(Cluster.class).toProvider(CassandraClusterProvider.class).asEagerSingleton();
        bind(Session.class).toProvider(CassandraSessionProvider.class);

        bind(ExecutionDAO.class).to(CassandraExecutionDAO.class);
    }
}
