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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.sql.Connection;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

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
    private String flywayTable = "schema_version";

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
     * The maximum lifetime (in minutes) of a connection in the pool
     */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration connectionMaxLifetime = Duration.ofMinutes(30);

    /**
     * The maximum amount of time (in minutes) that a connection is allowed to sit idle in the pool
     */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration connectionIdleTimeout = Duration.ofMinutes(10);

    /**
     * The maximum amount of time (in seconds) that a client will wait for a connection from the pool
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration connectionTimeout = Duration.ofSeconds(30);

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
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

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

    public String getFlywayTable() {
        return flywayTable;
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

    public Duration getConnectionMaxLifetime() {
        return connectionMaxLifetime;
    }

    public void setConnectionMaxLifetime(Duration connectionMaxLifetime) {
        this.connectionMaxLifetime = connectionMaxLifetime;
    }

    public Duration getConnectionIdleTimeout() {
        return connectionIdleTimeout;
    }

    public void setConnectionIdleTimeout(Duration connectionIdleTimeout) {
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
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

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }
}
