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

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JDBCInstanceConfigTest {

    @Test
    void testNewFormatSingleInstance() {
        Environment env = mock(Environment.class);
        JDBCInstanceConfig config = new JDBCInstanceConfig(env);

        JDBCConnectionConfig connectionConfig = new JDBCConnectionConfig();
        connectionConfig.setDatasourceURL("jdbc:h2:mem:test");
        connectionConfig.setMaximumPoolSize(5);

        JDBCInstanceConfig.JDBCInstance instance = new JDBCInstanceConfig.JDBCInstance();
        instance.setName("h2-test");
        instance.setConnection(connectionConfig);

        config.setInstances(List.of(instance));

        Map<String, DataSource> result = config.getJDBCInstances();

        assertEquals(1, result.size());
        assertNotNull(result.get("h2-test"));
        assertInstanceOf(HikariDataSource.class, result.get("h2-test"));

        // Cleanup
        ((HikariDataSource) result.get("h2-test")).close();
    }

    @Test
    void testNewFormatMultipleInstances() {
        Environment env = mock(Environment.class);
        JDBCInstanceConfig config = new JDBCInstanceConfig(env);

        JDBCConnectionConfig config1 = new JDBCConnectionConfig();
        config1.setDatasourceURL("jdbc:h2:mem:db1");
        JDBCInstanceConfig.JDBCInstance instance1 = new JDBCInstanceConfig.JDBCInstance();
        instance1.setName("db1");
        instance1.setConnection(config1);

        JDBCConnectionConfig config2 = new JDBCConnectionConfig();
        config2.setDatasourceURL("jdbc:h2:mem:db2");
        JDBCInstanceConfig.JDBCInstance instance2 = new JDBCInstanceConfig.JDBCInstance();
        instance2.setName("db2");
        instance2.setConnection(config2);

        config.setInstances(List.of(instance1, instance2));

        Map<String, DataSource> result = config.getJDBCInstances();

        assertEquals(2, result.size());
        assertNotNull(result.get("db1"));
        assertNotNull(result.get("db2"));

        // Cleanup
        result.values().forEach(ds -> ((HikariDataSource) ds).close());
    }

    @Test
    void testNewFormatSkipsInstanceWithNullConfig() {
        Environment env = mock(Environment.class);
        JDBCInstanceConfig config = new JDBCInstanceConfig(env);

        JDBCInstanceConfig.JDBCInstance instance = new JDBCInstanceConfig.JDBCInstance();
        instance.setName("broken");
        instance.setConnection(null);

        config.setInstances(List.of(instance));

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testEmptyInstances() {
        Environment env = mock(Environment.class);
        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(List.of());

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testNullInstances() {
        Environment env = mock(Environment.class);
        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(null);

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testLegacyFormatFallback() {
        Environment env = mock(Environment.class);
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn("mysql,postgres");

        // MySQL config
        when(env.getProperty("conductor.worker.jdbc.mysql.connectionURL"))
                .thenReturn("jdbc:h2:mem:mysql");
        when(env.getProperty("conductor.worker.jdbc.mysql.driverClassName"))
                .thenReturn("org.h2.Driver");
        when(env.getProperty("conductor.worker.jdbc.mysql.username")).thenReturn("root");
        when(env.getProperty("conductor.worker.jdbc.mysql.password")).thenReturn("pass");
        when(env.getProperty("conductor.worker.jdbc.mysql.maximum-pool-size", Integer.class, 10))
                .thenReturn(5);
        when(env.getProperty("conductor.worker.jdbc.mysql.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.mysql.minimum-idle", Integer.class, 1))
                .thenReturn(1);

        // Postgres config
        when(env.getProperty("conductor.worker.jdbc.postgres.connectionURL"))
                .thenReturn("jdbc:h2:mem:pg");
        when(env.getProperty("conductor.worker.jdbc.postgres.driverClassName"))
                .thenReturn("org.h2.Driver");
        when(env.getProperty("conductor.worker.jdbc.postgres.username")).thenReturn("pguser");
        when(env.getProperty("conductor.worker.jdbc.postgres.password")).thenReturn("pgpass");
        when(env.getProperty("conductor.worker.jdbc.postgres.maximum-pool-size", Integer.class, 10))
                .thenReturn(10);
        when(env.getProperty("conductor.worker.jdbc.postgres.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.postgres.minimum-idle", Integer.class, 1))
                .thenReturn(1);

        // Defaults
        when(env.getProperty("conductor.worker.jdbc.default.maximum-pool-size", Integer.class, 10))
                .thenReturn(10);
        when(env.getProperty("conductor.worker.jdbc.default.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.default.minimum-idle", Integer.class, 1))
                .thenReturn(1);

        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(null); // No new-format instances

        Map<String, DataSource> result = config.getJDBCInstances();

        assertEquals(2, result.size());
        assertNotNull(result.get("mysql"));
        assertNotNull(result.get("postgres"));

        HikariDataSource mysqlDs = (HikariDataSource) result.get("mysql");
        assertEquals("jdbc:h2:mem:mysql", mysqlDs.getJdbcUrl());
        assertEquals("root", mysqlDs.getUsername());
        assertEquals(5, mysqlDs.getMaximumPoolSize());

        HikariDataSource pgDs = (HikariDataSource) result.get("postgres");
        assertEquals("jdbc:h2:mem:pg", pgDs.getJdbcUrl());
        assertEquals("pguser", pgDs.getUsername());

        // Cleanup
        result.values().forEach(ds -> ((HikariDataSource) ds).close());
    }

    @Test
    void testLegacyFormatSkipsMissingURL() {
        Environment env = mock(Environment.class);
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn("broken");
        when(env.getProperty("conductor.worker.jdbc.broken.connectionURL")).thenReturn(null);
        when(env.getProperty("conductor.worker.jdbc.broken.driverClassName")).thenReturn(null);

        when(env.getProperty("conductor.worker.jdbc.default.maximum-pool-size", Integer.class, 10))
                .thenReturn(10);
        when(env.getProperty("conductor.worker.jdbc.default.idle-timeout-ms", Long.class, 300000L))
                .thenReturn(300000L);
        when(env.getProperty("conductor.worker.jdbc.default.minimum-idle", Integer.class, 1))
                .thenReturn(1);

        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(null);

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testLegacyFormatNotUsedWhenNewFormatConfigured() {
        Environment env = mock(Environment.class);
        // Legacy config exists but should be ignored
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn("legacy-db");

        JDBCInstanceConfig config = new JDBCInstanceConfig(env);

        JDBCConnectionConfig connectionConfig = new JDBCConnectionConfig();
        connectionConfig.setDatasourceURL("jdbc:h2:mem:new");
        JDBCInstanceConfig.JDBCInstance instance = new JDBCInstanceConfig.JDBCInstance();
        instance.setName("new-db");
        instance.setConnection(connectionConfig);

        config.setInstances(List.of(instance));

        Map<String, DataSource> result = config.getJDBCInstances();

        // Only new-format instance should be present
        assertEquals(1, result.size());
        assertNotNull(result.get("new-db"));
        assertNull(result.get("legacy-db"));

        // Legacy connectionIds should NOT have been read
        verify(env, never()).getProperty("conductor.worker.jdbc.connectionIds");

        // Cleanup
        result.values().forEach(ds -> ((HikariDataSource) ds).close());
    }

    @Test
    void testLegacyFormatNoConnectionIds() {
        Environment env = mock(Environment.class);
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn(null);

        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(null);

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testLegacyFormatBlankConnectionIds() {
        Environment env = mock(Environment.class);
        when(env.getProperty("conductor.worker.jdbc.connectionIds")).thenReturn("   ");

        JDBCInstanceConfig config = new JDBCInstanceConfig(env);
        config.setInstances(null);

        Map<String, DataSource> result = config.getJDBCInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void testJDBCInstanceGettersAndSetters() {
        JDBCInstanceConfig.JDBCInstance instance = new JDBCInstanceConfig.JDBCInstance();

        instance.setName("test-name");
        assertEquals("test-name", instance.getName());

        JDBCConnectionConfig conn = new JDBCConnectionConfig();
        instance.setConnection(conn);
        assertSame(conn, instance.getConnection());
    }
}
