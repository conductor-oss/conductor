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
package com.netflix.conductor.postgres.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.postgres.dao.PostgresExecutionDAO;
import com.netflix.conductor.postgres.dao.PostgresMetadataDAO;
import com.netflix.conductor.postgres.dao.PostgresQueueDAO;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "db", havingValue = "postgres")
public class PostgresConfiguration {

    @Bean
    public DataSource dataSource(PostgresProperties config) {
        return new PostgresDataSourceProvider(config).getDataSource();
    }

    @Bean
    public MetadataDAO postgresMetadataDAO(ObjectMapper objectMapper, DataSource dataSource,
        PostgresProperties properties) {
        return new PostgresMetadataDAO(objectMapper, dataSource, properties);
    }

    @Bean
    public ExecutionDAO postgresExecutionDAO(ObjectMapper objectMapper, DataSource dataSource) {
        return new PostgresExecutionDAO(objectMapper, dataSource);
    }

    @Bean
    public QueueDAO postgresQueueDAO(ObjectMapper objectMapper, DataSource dataSource) {
        return new PostgresQueueDAO(objectMapper, dataSource);
    }
}
