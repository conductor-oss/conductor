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

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.*;

class JDBCConnectionConfigTest {

    @Test
    void testCreateDataSourceWithAllProperties() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();
        config.setDatasourceURL("jdbc:postgresql://localhost:5432/test");
        config.setJdbcDriver("org.postgresql.Driver");
        config.setUser("testuser");
        config.setPassword("testpass");
        config.setMaximumPoolSize(10);
        config.setIdleTimeoutMs(60000L);
        config.setMinimumIdle(3);
        config.setLeakDetectionThreshold(30000L);
        config.setConnectionTimeout(15000L);
        config.setMaxLifetime(900000L);

        DataSource ds = config.createDataSource("test-pool");

        assertNotNull(ds);
        assertInstanceOf(HikariDataSource.class, ds);

        HikariDataSource hikari = (HikariDataSource) ds;
        assertEquals("test-pool", hikari.getPoolName());
        assertEquals("jdbc:postgresql://localhost:5432/test", hikari.getJdbcUrl());
        assertEquals("org.postgresql.Driver", hikari.getDriverClassName());
        assertEquals("testuser", hikari.getUsername());
        assertEquals("testpass", hikari.getPassword());
        assertEquals(10, hikari.getMaximumPoolSize());
        assertEquals(60000L, hikari.getIdleTimeout());
        assertEquals(3, hikari.getMinimumIdle());
        assertEquals(30000L, hikari.getLeakDetectionThreshold());
        assertEquals(15000L, hikari.getConnectionTimeout());
        assertEquals(900000L, hikari.getMaxLifetime());

        hikari.close();
    }

    @Test
    void testCreateDataSourceWithDefaults() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();
        config.setDatasourceURL("jdbc:h2:mem:test");

        DataSource ds = config.createDataSource("default-pool");

        assertNotNull(ds);
        HikariDataSource hikari = (HikariDataSource) ds;
        assertEquals("default-pool", hikari.getPoolName());
        assertEquals("jdbc:h2:mem:test", hikari.getJdbcUrl());
        assertEquals(32, hikari.getMaximumPoolSize());
        assertEquals(30000L, hikari.getIdleTimeout());
        assertEquals(2, hikari.getMinimumIdle());
        assertEquals(60000L, hikari.getLeakDetectionThreshold());
        assertEquals(30000L, hikari.getConnectionTimeout());
        assertEquals(1800000L, hikari.getMaxLifetime());

        hikari.close();
    }

    @Test
    void testCreateDataSourceWithNullDriver() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();
        config.setDatasourceURL("jdbc:h2:mem:test");
        config.setJdbcDriver(null);

        DataSource ds = config.createDataSource("no-driver-pool");

        assertNotNull(ds);
        HikariDataSource hikari = (HikariDataSource) ds;
        // Driver should be auto-detected from URL
        assertNull(hikari.getDriverClassName());

        hikari.close();
    }

    @Test
    void testCreateDataSourceWithBlankDriver() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();
        config.setDatasourceURL("jdbc:h2:mem:test");
        config.setJdbcDriver("   ");

        DataSource ds = config.createDataSource("blank-driver-pool");

        assertNotNull(ds);
        HikariDataSource hikari = (HikariDataSource) ds;
        assertNull(hikari.getDriverClassName());

        hikari.close();
    }

    @Test
    void testCreateDataSourceWithNullCredentials() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();
        config.setDatasourceURL("jdbc:h2:mem:test");
        config.setUser(null);
        config.setPassword(null);

        DataSource ds = config.createDataSource("no-creds-pool");

        assertNotNull(ds);
        HikariDataSource hikari = (HikariDataSource) ds;
        assertNull(hikari.getUsername());
        assertNull(hikari.getPassword());

        hikari.close();
    }

    @Test
    void testDefaultValues() {
        JDBCConnectionConfig config = new JDBCConnectionConfig();

        assertEquals(32, config.getMaximumPoolSize());
        assertEquals(30000L, config.getIdleTimeoutMs());
        assertEquals(2, config.getMinimumIdle());
        assertEquals(60000L, config.getLeakDetectionThreshold());
        assertEquals(30000L, config.getConnectionTimeout());
        assertEquals(1800000L, config.getMaxLifetime());
    }

    @Test
    void testAllArgsConstructor() {
        JDBCConnectionConfig config =
                new JDBCConnectionConfig(
                        "jdbc:mysql://localhost/db",
                        "com.mysql.cj.jdbc.Driver",
                        "user",
                        "pass",
                        20,
                        45000L,
                        5,
                        50000L,
                        20000L,
                        600000L);

        assertEquals("jdbc:mysql://localhost/db", config.getDatasourceURL());
        assertEquals("com.mysql.cj.jdbc.Driver", config.getJdbcDriver());
        assertEquals("user", config.getUser());
        assertEquals("pass", config.getPassword());
        assertEquals(20, config.getMaximumPoolSize());
        assertEquals(45000L, config.getIdleTimeoutMs());
        assertEquals(5, config.getMinimumIdle());
        assertEquals(50000L, config.getLeakDetectionThreshold());
        assertEquals(20000L, config.getConnectionTimeout());
        assertEquals(600000L, config.getMaxLifetime());
    }
}
