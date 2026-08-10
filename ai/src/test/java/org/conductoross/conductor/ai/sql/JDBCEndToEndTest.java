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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.PostgreSQLContainer;

import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end tests for the JDBC configuration and execution pipeline using a real PostgreSQL
 * database via TestContainers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JDBCEndToEndTest {

    private static PostgreSQLContainer<?> postgres;
    private JDBCProvider provider;

    @BeforeAll
    void setup() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();

        // Configure instances using new format
        JDBCConnectionConfig connectionConfig = new JDBCConnectionConfig();
        connectionConfig.setDatasourceURL(postgres.getJdbcUrl());
        connectionConfig.setJdbcDriver("org.postgresql.Driver");
        connectionConfig.setUser(postgres.getUsername());
        connectionConfig.setPassword(postgres.getPassword());
        connectionConfig.setMaximumPoolSize(5);
        connectionConfig.setMinimumIdle(1);

        JDBCInstanceConfig.JDBCInstance instance = new JDBCInstanceConfig.JDBCInstance();
        instance.setName("pg-test");
        instance.setConnection(connectionConfig);

        Environment env = mock(Environment.class);
        JDBCInstanceConfig instanceConfig = new JDBCInstanceConfig(env);
        instanceConfig.setInstances(List.of(instance));

        provider = new JDBCProvider(instanceConfig);
    }

    @AfterAll
    void teardown() {
        if (provider != null) {
            provider.shutdown();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static JDBCInput inputFor(String connectionId) {
        JDBCInput input = new JDBCInput();
        input.setConnectionId(connectionId);
        return input;
    }

    @Test
    @Order(1)
    void testProviderReturnsDataSource() {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);
        assertInstanceOf(HikariDataSource.class, ds);
    }

    @Test
    @Order(2)
    void testCreateTableAndInsertData() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);

        try (Connection conn = ds.getConnection()) {
            // Create table
            try (PreparedStatement stmt =
                    conn.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS test_table ("
                                    + "id SERIAL PRIMARY KEY, "
                                    + "name VARCHAR(255), "
                                    + "value INTEGER)")) {
                stmt.execute();
            }

            // Insert rows
            try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO test_table (name, value) VALUES (?, ?)")) {
                stmt.setString(1, "alpha");
                stmt.setInt(2, 100);
                stmt.executeUpdate();

                stmt.setString(1, "beta");
                stmt.setInt(2, 200);
                stmt.executeUpdate();

                stmt.setString(1, "gamma");
                stmt.setInt(2, 300);
                stmt.executeUpdate();
            }
        }
    }

    @Test
    @Order(3)
    void testSelectData() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);

        try (Connection conn = ds.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT name, value FROM test_table ORDER BY value")) {
            var rs = stmt.executeQuery();

            assertTrue(rs.next());
            assertEquals("alpha", rs.getString("name"));
            assertEquals(100, rs.getInt("value"));

            assertTrue(rs.next());
            assertEquals("beta", rs.getString("name"));
            assertEquals(200, rs.getInt("value"));

            assertTrue(rs.next());
            assertEquals("gamma", rs.getString("name"));
            assertEquals(300, rs.getInt("value"));

            assertFalse(rs.next());
        }
    }

    @Test
    @Order(4)
    void testSelectWithParameters() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);

        try (Connection conn = ds.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT name, value FROM test_table WHERE value > ?")) {
            stmt.setInt(1, 150);
            var rs = stmt.executeQuery();

            assertTrue(rs.next()); // beta (200)
            assertTrue(rs.next()); // gamma (300)
            assertFalse(rs.next());
        }
    }

    @Test
    @Order(5)
    void testUpdateData() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt =
                    conn.prepareStatement("UPDATE test_table SET value = ? WHERE name = ?")) {
                stmt.setInt(1, 999);
                stmt.setString(2, "alpha");
                int count = stmt.executeUpdate();
                assertEquals(1, count);
            }
            conn.commit();

            // Verify update
            try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT value FROM test_table WHERE name = ?")) {
                stmt.setString(1, "alpha");
                var rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(999, rs.getInt("value"));
            }
        }
    }

    @Test
    @Order(6)
    void testTransactionRollback() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt =
                    conn.prepareStatement("UPDATE test_table SET value = 0 WHERE name = ?")) {
                stmt.setString(1, "beta");
                stmt.executeUpdate();
            }
            conn.rollback();

            // Verify rollback - value should still be 200
            try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT value FROM test_table WHERE name = ?")) {
                stmt.setString(1, "beta");
                var rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(200, rs.getInt("value"));
            }
        }
    }

    @Test
    @Order(7)
    void testConnectionPooling() throws SQLException {
        DataSource ds = provider.get(inputFor("pg-test"));
        assertNotNull(ds);
        assertInstanceOf(HikariDataSource.class, ds);

        HikariDataSource hikari = (HikariDataSource) ds;
        assertEquals(5, hikari.getMaximumPoolSize());
        assertEquals(1, hikari.getMinimumIdle());

        // Open and close multiple connections - should reuse from pool
        for (int i = 0; i < 10; i++) {
            try (Connection conn = ds.getConnection()) {
                assertNotNull(conn);
                assertFalse(conn.isClosed());
            }
        }

        // Pool should still be running
        assertTrue(hikari.isRunning());
    }

    @Test
    @Order(8)
    void testUnknownInstanceReturnsNull() {
        DataSource ds = provider.get(inputFor("nonexistent"));
        assertNull(ds);
    }

    @Test
    @Order(9)
    void testLegacyFormatEndToEnd() {
        // Simulate legacy Environment-based configuration
        Environment env = mock(Environment.class);
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn("pg-legacy");
        when(env.getProperty("conductor.worker.jdbc.pg-legacy.connectionURL"))
                .thenReturn(postgres.getJdbcUrl());
        when(env.getProperty("conductor.worker.jdbc.pg-legacy.driverClassName"))
                .thenReturn("org.postgresql.Driver");
        when(env.getProperty("conductor.worker.jdbc.pg-legacy.username"))
                .thenReturn(postgres.getUsername());
        when(env.getProperty("conductor.worker.jdbc.pg-legacy.password"))
                .thenReturn(postgres.getPassword());
        when(env.getProperty(
                        "conductor.worker.jdbc.pg-legacy.maximum-pool-size", Integer.class, 10))
                .thenReturn(3);
        when(env.getProperty(
                        "conductor.worker.jdbc.pg-legacy.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.pg-legacy.minimum-idle", Integer.class, 1))
                .thenReturn(1);
        when(env.getProperty("conductor.worker.jdbc.default.maximum-pool-size", Integer.class, 10))
                .thenReturn(10);
        when(env.getProperty("conductor.worker.jdbc.default.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.default.minimum-idle", Integer.class, 1))
                .thenReturn(1);

        JDBCInstanceConfig instanceConfig = new JDBCInstanceConfig(env);
        instanceConfig.setInstances(null); // Force legacy fallback

        JDBCProvider legacyProvider = new JDBCProvider(instanceConfig);
        DataSource legacyDs = legacyProvider.get(inputFor("pg-legacy"));

        assertNotNull(legacyDs);

        // Verify it can actually connect to the database
        try (Connection conn = legacyDs.getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM test_table")) {
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0);
        } catch (SQLException e) {
            fail("Legacy datasource should be able to query: " + e.getMessage());
        }

        legacyProvider.shutdown();
    }

    @Test
    @Order(10)
    void testMultipleInstancesSameDatabase() {
        // Configure two instances pointing to same database with different pool settings
        JDBCConnectionConfig config1 = new JDBCConnectionConfig();
        config1.setDatasourceURL(postgres.getJdbcUrl());
        config1.setJdbcDriver("org.postgresql.Driver");
        config1.setUser(postgres.getUsername());
        config1.setPassword(postgres.getPassword());
        config1.setMaximumPoolSize(2);

        JDBCConnectionConfig config2 = new JDBCConnectionConfig();
        config2.setDatasourceURL(postgres.getJdbcUrl());
        config2.setJdbcDriver("org.postgresql.Driver");
        config2.setUser(postgres.getUsername());
        config2.setPassword(postgres.getPassword());
        config2.setMaximumPoolSize(3);

        JDBCInstanceConfig.JDBCInstance instance1 = new JDBCInstanceConfig.JDBCInstance();
        instance1.setName("reader");
        instance1.setConnection(config1);

        JDBCInstanceConfig.JDBCInstance instance2 = new JDBCInstanceConfig.JDBCInstance();
        instance2.setName("writer");
        instance2.setConnection(config2);

        Environment env = mock(Environment.class);
        JDBCInstanceConfig instanceConfig = new JDBCInstanceConfig(env);
        instanceConfig.setInstances(List.of(instance1, instance2));

        JDBCProvider multiProvider = new JDBCProvider(instanceConfig);

        DataSource readerDs = multiProvider.get(inputFor("reader"));
        DataSource writerDs = multiProvider.get(inputFor("writer"));

        assertNotNull(readerDs);
        assertNotNull(writerDs);
        assertNotSame(readerDs, writerDs);

        assertEquals(2, ((HikariDataSource) readerDs).getMaximumPoolSize());
        assertEquals(3, ((HikariDataSource) writerDs).getMaximumPoolSize());

        multiProvider.shutdown();
    }
}
