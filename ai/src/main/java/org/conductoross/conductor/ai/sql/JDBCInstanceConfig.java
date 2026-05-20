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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Main configuration class for JDBC instances. Supports multiple named instances via list-based
 * configuration.
 *
 * <p>Configuration example:
 *
 * <pre>
 * conductor.jdbc.instances:
 *   - name: "mysql-prod"
 *     connection:
 *       datasourceURL: "jdbc:mysql://prod:3306/db"
 *       jdbcDriver: "com.mysql.cj.jdbc.Driver"
 *       user: "admin"
 *       password: "secret"
 *   - name: "postgres-analytics"
 *     connection:
 *       datasourceURL: "jdbc:postgresql://analytics:5432/data"
 *       user: "reader"
 *       password: "secret"
 * </pre>
 *
 * <p>Legacy format (backwards compatibility):
 *
 * <pre>
 * conductor.worker.jdbc.connectionIds: mysql,postgres
 * conductor.worker.jdbc.mysql.connectionURL: jdbc:mysql://localhost:3306/db
 * conductor.worker.jdbc.mysql.driverClassName: com.mysql.cj.jdbc.Driver
 * conductor.worker.jdbc.mysql.username: root
 * conductor.worker.jdbc.mysql.password: secret
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "conductor.jdbc")
@Slf4j
public class JDBCInstanceConfig {

    private List<JDBCInstance> instances;

    private final Environment env;

    public JDBCInstanceConfig(Environment env) {
        this.env = env;
    }

    public List<JDBCInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<JDBCInstance> instances) {
        this.instances = instances;
    }

    /**
     * Returns a map of DataSource instances keyed by their configured names. Falls back to legacy
     * configuration format if no new-style instances are configured.
     */
    public Map<String, DataSource> getJDBCInstances() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();

        if (instances != null && !instances.isEmpty()) {
            for (JDBCInstance instance : instances) {
                try {
                    JDBCConnectionConfig config = instance.getConnection();
                    if (config == null) {
                        log.error(
                                "Connection configuration missing for JDBC instance: {}",
                                instance.getName());
                        continue;
                    }
                    DataSource ds = config.createDataSource(instance.getName());
                    dataSourceMap.put(instance.getName(), ds);
                    log.info("Initialized JDBC instance: {}", instance.getName());
                } catch (Exception e) {
                    log.error(
                            "Failed to initialize JDBC instance: {}, reason: {}",
                            instance.getName(),
                            e.getMessage());
                }
            }
        }

        // Legacy format: conductor.worker.jdbc.connectionIds (backwards compatibility)
        if (dataSourceMap.isEmpty()) {
            Map<String, DataSource> legacyInstances = getLegacyInstances();
            dataSourceMap.putAll(legacyInstances);
        }

        return dataSourceMap;
    }

    /**
     * Reads legacy configuration from conductor.worker.jdbc.connectionIds format. Preserved for
     * backwards compatibility with existing deployments.
     */
    private Map<String, DataSource> getLegacyInstances() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        String prefix = "conductor.worker.jdbc.";

        String connectionIds = env.getProperty(prefix + "connectionIds");
        if (connectionIds == null || connectionIds.isBlank()) {
            return dataSourceMap;
        }

        log.info("Reading legacy JDBC configuration from conductor.worker.jdbc.*");

        int defaultMaxPoolSize =
                env.getProperty(prefix + "default.maximum-pool-size", Integer.class, 10);
        long defaultIdleTimeoutMs =
                env.getProperty(prefix + "default.idle-timeout-ms", Long.class, 300000L);
        int defaultMinimumIdle = env.getProperty(prefix + "default.minimum-idle", Integer.class, 1);

        String[] ids = connectionIds.split(",");
        for (String id : ids) {
            id = id.trim();
            String connectionURL = env.getProperty(prefix + id + ".connectionURL");
            String driverClassName = env.getProperty(prefix + id + ".driverClassName");
            if (Strings.isBlank(connectionURL) || Strings.isBlank(driverClassName)) {
                continue;
            }

            JDBCConnectionConfig config = new JDBCConnectionConfig();
            config.setDatasourceURL(connectionURL);
            config.setJdbcDriver(driverClassName);
            config.setUser(env.getProperty(prefix + id + ".username"));
            config.setPassword(env.getProperty(prefix + id + ".password"));
            config.setMaximumPoolSize(
                    env.getProperty(
                            prefix + id + ".maximum-pool-size", Integer.class, defaultMaxPoolSize));
            config.setIdleTimeoutMs(
                    env.getProperty(
                            prefix + id + ".idle-timeout-ms", Long.class, defaultIdleTimeoutMs));
            config.setMinimumIdle(
                    env.getProperty(
                            prefix + id + ".minimum-idle", Integer.class, defaultMinimumIdle));

            DataSource ds = config.createDataSource(id);
            log.info("Initialized legacy JDBC instance: {}", id);
            dataSourceMap.put(id, ds);
        }

        return dataSourceMap;
    }

    /** Represents a single JDBC instance configuration. */
    public static class JDBCInstance {
        private String name;
        private JDBCConnectionConfig connection;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public JDBCConnectionConfig getConnection() {
            return connection;
        }

        public void setConnection(JDBCConnectionConfig connection) {
            this.connection = connection;
        }
    }
}
