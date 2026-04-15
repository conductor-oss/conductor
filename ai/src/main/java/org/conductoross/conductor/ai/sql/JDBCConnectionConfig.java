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

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JDBCConnectionConfig {

    private String datasourceURL;

    private String jdbcDriver;

    private String user;

    private String password;

    // Hikari pool settings with defaults
    private Integer maximumPoolSize = 32;

    private Long idleTimeoutMs = 30000L;

    private Integer minimumIdle = 2;

    private Long leakDetectionThreshold = 60000L;

    private Long connectionTimeout = 30000L;

    private Long maxLifetime = 1800000L;

    /**
     * Creates a configured HikariCP DataSource from this configuration.
     *
     * @param name Pool name for identification and logging
     * @return A configured DataSource
     */
    public DataSource createDataSource(String name) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(name);
        ds.setJdbcUrl(datasourceURL);
        if (jdbcDriver != null && !jdbcDriver.isBlank()) {
            ds.setDriverClassName(jdbcDriver);
        }
        if (user != null) {
            ds.setUsername(user);
        }
        if (password != null) {
            ds.setPassword(password);
        }
        ds.setMaximumPoolSize(maximumPoolSize != null ? maximumPoolSize : 32);
        ds.setIdleTimeout(idleTimeoutMs != null ? idleTimeoutMs : 30000L);
        ds.setMinimumIdle(minimumIdle != null ? minimumIdle : 2);
        ds.setLeakDetectionThreshold(
                leakDetectionThreshold != null ? leakDetectionThreshold : 60000L);
        ds.setConnectionTimeout(connectionTimeout != null ? connectionTimeout : 30000L);
        ds.setMaxLifetime(maxLifetime != null ? maxLifetime : 1800000L);
        return ds;
    }
}
