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

import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.postgres")
public class PostgresProperties {

    /**
     * The jdbc url to be used for connecting to the database
     */
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/conductor";

    /**
     * The username to be used for connections
     */
    private String jdbcUsername = "conductor";

    /**
     * The password to be used for connections
     */
    private String jdbcPassword = "password";

    /**
     * Used to enable/disable flyway migrations
     */
    private boolean flywayEnabled = true;

    /**
     * Used to override the default flyway migration table
     */
    private String flywayTable = null;

    // The defaults are currently in line with the HikariConfig defaults, which are unfortunately private.
    /**
     * The maximum size that the connection pool is allowed to reach including idle and in-use connections
     */
    private int connectionPoolMaxSize = -1;

    /**
     * The minimum number of idle connections that the connection pool tries to maintain in the pool
     */
    private int connectionPoolMinIdle = -1;

    /**
     * The maximum lifetime (in milliseconds) of a connection in the pool
     */
    private long connectionMaxLifetime = TimeUnit.MINUTES.toMillis(30);

    /**
     * The maximum amount of time (in milliseconds) that a connection is allowed to sit idle in the pool
     */
    private long connectionIdleTimeout = TimeUnit.MINUTES.toMillis(30);

    /**
     * The maximum amount of time (in milliseconds) that a client will wait for a connection from the pool
     */
    private long connectionTimeout = TimeUnit.MINUTES.toMillis(30);

    /**
     * The transaction isolation level as specified in {@link Connection}
     */
    private String transactionIsolationLevel = "";

    // This is consistent with the current default when building the Hikari Client.
    /**
     * The auto-commit behavior of the connections in the pool
     */
    private boolean autoCommit = false;

    /**
     * The time in seconds after which the in-memory task definitions cache will be refreshed
     */
    private int taskDefCacheRefreshTimeSecs = 60;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getJdbcUsername() {
        return jdbcUsername;
    }

    public void setJdbcUsername(String jdbcUsername) {
        this.jdbcUsername = jdbcUsername;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public void setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    public boolean isFlywayEnabled() {
        return flywayEnabled;
    }

    public void setFlywayEnabled(boolean flywayEnabled) {
        this.flywayEnabled = flywayEnabled;
    }

    public Optional<String> getFlywayTable() {
        return Optional.ofNullable(flywayTable);
    }

    public void setFlywayTable(String flywayTable) {
        this.flywayTable = flywayTable;
    }

    public int getConnectionPoolMaxSize() {
        return connectionPoolMaxSize;
    }

    public void setConnectionPoolMaxSize(int connectionPoolMaxSize) {
        this.connectionPoolMaxSize = connectionPoolMaxSize;
    }

    public int getConnectionPoolMinIdle() {
        return connectionPoolMinIdle;
    }

    public void setConnectionPoolMinIdle(int connectionPoolMinIdle) {
        this.connectionPoolMinIdle = connectionPoolMinIdle;
    }

    public long getConnectionMaxLifetime() {
        return connectionMaxLifetime;
    }

    public void setConnectionMaxLifetime(long connectionMaxLifetime) {
        this.connectionMaxLifetime = connectionMaxLifetime;
    }

    public long getConnectionIdleTimeout() {
        return connectionIdleTimeout;
    }

    public void setConnectionIdleTimeout(long connectionIdleTimeout) {
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getTransactionIsolationLevel() {
        return transactionIsolationLevel;
    }

    public void setTransactionIsolationLevel(String transactionIsolationLevel) {
        this.transactionIsolationLevel = transactionIsolationLevel;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public int getTaskDefCacheRefreshTimeSecs() {
        return taskDefCacheRefreshTimeSecs;
    }

    public void setTaskDefCacheRefreshTimeSecs(int taskDefCacheRefreshTimeSecs) {
        this.taskDefCacheRefreshTimeSecs = taskDefCacheRefreshTimeSecs;
    }
}
