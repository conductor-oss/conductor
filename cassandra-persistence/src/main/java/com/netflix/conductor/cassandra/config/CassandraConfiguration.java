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
package com.netflix.conductor.cassandra.config;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.cassandra.dao.CassandraEventHandlerDAO;
import com.netflix.conductor.cassandra.dao.CassandraExecutionDAO;
import com.netflix.conductor.cassandra.dao.CassandraMetadataDAO;
import com.netflix.conductor.cassandra.util.Statements;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "db", havingValue = "cassandra")
public class CassandraConfiguration {

    @Bean
    public Cluster cluster(CassandraProperties properties) {
        return new CassandraClusterProvider(properties).get();
    }

    @Bean
    public Session session(Cluster cluster) {
        return new CassandraSessionProvider(cluster).get();
    }

    @Bean
    public MetadataDAO cassandraMetadataDAO(Session session, ObjectMapper objectMapper, CassandraProperties properties,
        Statements statements) {
        return new CassandraMetadataDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public ExecutionDAO cassandraExecutionDAO(Session session, ObjectMapper objectMapper,
        CassandraProperties properties, Statements statements) {
        return new CassandraExecutionDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public EventHandlerDAO cassandraEventHandlerDAO(Session session, ObjectMapper objectMapper,
        CassandraProperties properties, Statements statements) {
        return new CassandraEventHandlerDAO(session, objectMapper, properties, statements);
    }
}
