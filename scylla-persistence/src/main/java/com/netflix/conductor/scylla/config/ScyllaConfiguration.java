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
package com.netflix.conductor.scylla.config;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.scylla.config.cache.CacheableEventHandlerDAO;
import com.netflix.conductor.scylla.config.cache.CacheableMetadataDAO;
import com.netflix.conductor.scylla.dao.ScyllaEventHandlerDAO;
import com.netflix.conductor.scylla.dao.ScyllaExecutionDAO;
import com.netflix.conductor.scylla.dao.ScyllaMetadataDAO;
import com.netflix.conductor.scylla.dao.ScyllaPollDataDAO;
import com.netflix.conductor.scylla.dao.ScyllaRateLimitingDAO;
import com.netflix.conductor.scylla.util.Statements;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScyllaProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "scylla")
public class ScyllaConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaConfiguration.class);

    @Bean
    public Cluster cluster(ScyllaProperties properties) {
        String host = properties.getHostAddress();
        int port = properties.getPort();

        LOGGER.info("Connecting to scylla cluster with host:{}, port:{}", host, port);

        Cluster cluster = Cluster.builder()
                .withoutJMXReporting()
                .withProtocolVersion(ProtocolVersion.V3)
                .addContactPoint(host)
                .withCredentials(properties.getUserName(), properties.getPassword())
                .withPort(port).build();

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
        LOGGER.info("Initializing scylla session");
        return cluster.connect();
    }

    @Bean
    public QueryLogger queryLogger(Cluster cluster) {
        QueryLogger queryLogger = QueryLogger.builder()
                .build();
        cluster.register(queryLogger);
        return queryLogger;
    }

    @Bean
    public MetadataDAO scyllaMetadataDAO(
            Session session,
            ObjectMapper objectMapper,
            ScyllaProperties properties,
            Statements statements,
            CacheManager cacheManager) {
        ScyllaMetadataDAO scyllaMetadataDAO =
                new ScyllaMetadataDAO(session, objectMapper, properties, statements);
        return new CacheableMetadataDAO(scyllaMetadataDAO, properties, cacheManager);
    }

    @Bean
    public ExecutionDAO scyllaExecutionDAO(
            Session session,
            ObjectMapper objectMapper,
            ScyllaProperties properties,
            Statements statements) {
        return new ScyllaExecutionDAO(session, objectMapper, properties, statements);
    }

    @Bean
    public EventHandlerDAO scyllaEventHandlerDAO(
            Session session,
            ObjectMapper objectMapper,
            ScyllaProperties properties,
            Statements statements,
            CacheManager cacheManager) {
        ScyllaEventHandlerDAO scyllaEventHandlerDAO =
                new ScyllaEventHandlerDAO(session, objectMapper, properties, statements);
        return new CacheableEventHandlerDAO(scyllaEventHandlerDAO, properties, cacheManager);
    }

    @Bean
    public ScyllaPollDataDAO scyllaPollDataDAO() {
        return new ScyllaPollDataDAO();
    }

    @Bean
    public Statements statements(ScyllaProperties scyllaProperties) {
        return new Statements(scyllaProperties.getKeyspace());
    }
    @Bean
    public RateLimitingDAO scyllaRateLimitingDAO() {
        return new ScyllaRateLimitingDAO();
    }
}
