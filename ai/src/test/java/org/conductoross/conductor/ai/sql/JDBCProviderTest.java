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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JDBCProviderTest {

    @Test
    void testEmptyConfigList() {
        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(Collections.emptyMap());

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        DataSource result = provider.get("mysql-prod");

        assertNull(result);
    }

    @Test
    void testGetRegisteredInstance() {
        DataSource mockDataSource = mock(DataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("mysql-prod", mockDataSource);

        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(instances);

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        DataSource result = provider.get("mysql-prod");

        assertNotNull(result);
        assertSame(mockDataSource, result);
    }

    @Test
    void testGetUnregisteredInstance() {
        DataSource mockDataSource = mock(DataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("mysql-prod", mockDataSource);

        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(instances);

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        DataSource result = provider.get("unknown");

        assertNull(result);
    }

    @Test
    void testMultipleInstances() {
        DataSource mockMysql = mock(DataSource.class);
        DataSource mockPostgres = mock(DataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("mysql-prod", mockMysql);
        instances.put("postgres-analytics", mockPostgres);

        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(instances);

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        assertSame(mockMysql, provider.get("mysql-prod"));
        assertSame(mockPostgres, provider.get("postgres-analytics"));
    }

    @Test
    void testGetWithNullName() {
        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(Collections.emptyMap());

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        DataSource result = provider.get(null);
        assertNull(result);
    }

    @Test
    void testConfigExceptionDoesNotPropagate() {
        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenThrow(new RuntimeException("Config error"));

        // Should not throw - exception is caught and logged
        JDBCProvider provider = new JDBCProvider(instanceConfig);

        assertNull(provider.get("anything"));
    }

    @Test
    void testShutdownClosesHikariPools() {
        com.zaxxer.hikari.HikariDataSource mockHikari1 =
                mock(com.zaxxer.hikari.HikariDataSource.class);
        com.zaxxer.hikari.HikariDataSource mockHikari2 =
                mock(com.zaxxer.hikari.HikariDataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("ds1", mockHikari1);
        instances.put("ds2", mockHikari2);

        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(instances);

        JDBCProvider provider = new JDBCProvider(instanceConfig);
        provider.shutdown();

        verify(mockHikari1).close();
        verify(mockHikari2).close();

        // After shutdown, instances should be cleared
        assertNull(provider.get("ds1"));
        assertNull(provider.get("ds2"));
    }
}
