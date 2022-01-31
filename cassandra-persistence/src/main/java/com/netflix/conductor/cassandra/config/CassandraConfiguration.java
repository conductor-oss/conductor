/*
 * Copyright 2022 Netflix, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.cassandra.dao.CassandraEventHandlerDAO;
import com.netflix.conductor.cassandra.dao.CassandraExecutionDAO;
import com.netflix.conductor.cassandra.dao.CassandraMetadataDAO;
import com.netflix.conductor.cassandra.dao.CassandraPollDataDAO;
import com.netflix.conductor.cassandra.util.Statements;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CassandraProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "cassandra")
public class CassandraConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraConfiguration.class);

    @Bean
    public Cluster cluster(CassandraProperties properties) {
        String host = properties.getHostAddress();
        int port = properties.getPort();

        LOGGER.info("Connecting to cassandra cluster with host:{}, port:{}", host, port);

        Cluster cluster = Cluster.builder().addContactPoint(host).withPort(port).build();

        Metadata metadata = cluster.getMetadata();
        LOGGER.info("Connected to cluster: {}", metadata.getClusterName());
        metadata.getAllHosts()
                .forEach(
                        h ->
                                LOGGER.info(
                                        "Datacenter:{}, host:{}, rack: {}",
                                        h.getDatacenter(),
                                        h.getEndPoint().resolve().getHostName(),
                                        h.getRack()));
        return cluster;
    }

    @Bean
    public Session session(Cluster cluster) {
        LOGGER.info("Initializing cassandra session");
        return cluster.connect();
    }

    @Bean
    public MetadataDAO cassandraMetadataDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            Statements statements) {
        return new CassandraMetadataDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public ExecutionDAO cassandraExecutionDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            Statements statements) {
        return new CassandraExecutionDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public EventHandlerDAO cassandraEventHandlerDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            Statements statements) {
        return new CassandraEventHandlerDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public CassandraPollDataDAO cassandraPollDataDAO() {
        return new CassandraPollDataDAO();
    }

    @Bean
    public Statements statements(CassandraProperties cassandraProperties) {
        return new Statements(cassandraProperties.getKeyspace());
    }
}
