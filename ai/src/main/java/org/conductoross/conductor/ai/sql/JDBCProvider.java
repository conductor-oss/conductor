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
package org.conductoross.conductor.ai.sql;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider for managing multiple JDBC DataSource instances. Uses name-based lookup to support
 * multiple database connections.
 *
 * <p>The provider is initialized with a JDBCInstanceConfig which contains all configured JDBC
 * instances (both new-style and legacy format).
 */
@Component
@Slf4j
public class JDBCProvider {

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    /**
     * Initializes the provider with configured JDBC instances.
     *
     * @param instanceConfig Configuration containing all JDBC instances
     */
    public JDBCProvider(JDBCInstanceConfig instanceConfig) {
        try {
            Map<String, DataSource> instances = instanceConfig.getJDBCInstances();
            dataSources.putAll(instances);
            log.info("Initialized JDBCProvider with {} instances", dataSources.size());
            dataSources.keySet().forEach(name -> log.info("  - {}", name));
        } catch (Exception e) {
            log.error("Failed to initialize JDBCProvider: {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieves a DataSource by its configured name.
     *
     * @param input input
     * @return The DataSource, or null if not found
     */
    public DataSource get(JDBCInput input) {
        String name =
                Optional.ofNullable(input.getConnectionId()).orElse(input.getIntegrationName());
        if (name == null) {
            log.warn("JDBC instance name is null");
            return null;
        }
        DataSource ds = dataSources.get(name);
        if (ds == null) {
            log.warn(
                    "JDBC instance not found: {}. Available instances: {}",
                    name,
                    dataSources.keySet());
        }
        return ds;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down JDBCProvider, closing all DataSource pools");
        dataSources
                .values()
                .forEach(
                        ds -> {
                            if (ds instanceof HikariDataSource) {
                                ((HikariDataSource) ds).close();
                            }
                        });
        dataSources.clear();
    }
}
