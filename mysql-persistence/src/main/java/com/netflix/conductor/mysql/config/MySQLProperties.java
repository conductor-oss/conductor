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
package com.netflix.conductor.mysql.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "db", havingValue = "mysql")
public class MySQLProperties {

    @Value("${jdbc.url:jdbc:mysql://localhost:3306/conductor}")
    private String jdbcUrl;

    @Value("${jdbc.username:conductor}")
    private String jdbcUsername;

    @Value("${jdbc.password:password}")
    private String jdbcPassword;

    @Value("${flyway.enabled:true}")
    private boolean flywayEnabled;

    @Value("${flyway.table:#{null}}")
    private String flywayTable;

    // The defaults are currently in line with the HikariConfig defaults, which are unfortunately private.
    @Value("${conductor.mysql.connection.pool.size.max:-1}")
    private int connectionPoolMaxSize;

    @Value("${conductor.mysql.connection.pool.idle.min:-1}")
    private int connectionPoolMinIdle;

    @Value("${conductor.mysql.connection.lifetime.max:#{T(java.util.concurrent.TimeUnit).MINUTES.toMillis(30)}}")
    private long connectionMaxLifetime;

    @Value("${conductor.mysql.connection.idle.timeout:#{T(java.util.concurrent.TimeUnit).MINUTES.toMillis(10)}}")
    private long connectionIdleTimeout;

    @Value("${conductor.mysql.connection.timeout:#{T(java.util.concurrent.TimeUnit).MINUTES.toMillis(30)}}")
    private long connectionTimeout;

    @Value("${conductor.mysql.transaction.isolation.level:}")
    private String transactionIsolationLevel;

    /**
     * This is consistent with the current default when building the Hikari Client.
     */
    @Value("${conductor.mysql.autocommit:false}")
    private boolean autoCommit;

    /**
     * the refresh time for the in-memory task definition cache
     */
    @Value("${conductor.taskdef.cache.refresh.time.seconds:60}")
    private int taskDefCacheRefreshTimeSecs;


    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getJdbcUserName() {
        return jdbcUsername;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public boolean isFlywayEnabled() {
        return flywayEnabled;
    }

    public Optional<String> getFlywayTable() {
        return Optional.ofNullable(flywayTable);
    }

    public int getConnectionPoolMaxSize() {
        return connectionPoolMaxSize;
    }

    public int getConnectionPoolMinIdle() {
        return connectionPoolMinIdle;
    }

    public long getConnectionMaxLifetime() {
        return connectionMaxLifetime;
    }

    public long getConnectionIdleTimeout() {
        return connectionIdleTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public String getTransactionIsolationLevel() {
        return transactionIsolationLevel;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public int getTaskDefRefreshTimeSecs() {
        return taskDefCacheRefreshTimeSecs;
    }
}
