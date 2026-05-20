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

    private JDBCProvider createProvider(Map<String, DataSource> instances) {
        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenReturn(instances);
        return new JDBCProvider(instanceConfig);
    }

    private JDBCInput inputWithConnectionId(String connectionId) {
        JDBCInput input = new JDBCInput();
        input.setConnectionId(connectionId);
        return input;
    }

    private JDBCInput inputWithIntegrationName(String integrationName) {
        JDBCInput input = new JDBCInput();
        input.setIntegrationName(integrationName);
        return input;
    }

    @Test
    void testEmptyConfigList() {
        JDBCProvider provider = createProvider(Collections.emptyMap());
        assertNull(provider.get(inputWithConnectionId("mysql-prod")));
    }

    @Test
    void testGetByConnectionId() {
        DataSource mockDataSource = mock(DataSource.class);
        JDBCProvider provider = createProvider(Map.of("mysql-prod", mockDataSource));

        assertSame(mockDataSource, provider.get(inputWithConnectionId("mysql-prod")));
    }

    @Test
    void testGetByIntegrationName() {
        DataSource mockDataSource = mock(DataSource.class);
        JDBCProvider provider = createProvider(Map.of("my-integration", mockDataSource));

        assertSame(mockDataSource, provider.get(inputWithIntegrationName("my-integration")));
    }

    @Test
    void testConnectionIdTakesPrecedenceOverIntegrationName() {
        DataSource connDs = mock(DataSource.class);
        DataSource integDs = mock(DataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("conn-id", connDs);
        instances.put("integ-name", integDs);
        JDBCProvider provider = createProvider(instances);

        JDBCInput input = new JDBCInput();
        input.setConnectionId("conn-id");
        input.setIntegrationName("integ-name");

        assertSame(connDs, provider.get(input));
    }

    @Test
    void testGetUnregisteredInstance() {
        DataSource mockDataSource = mock(DataSource.class);
        JDBCProvider provider = createProvider(Map.of("mysql-prod", mockDataSource));

        assertNull(provider.get(inputWithConnectionId("unknown")));
    }

    @Test
    void testMultipleInstances() {
        DataSource mockMysql = mock(DataSource.class);
        DataSource mockPostgres = mock(DataSource.class);

        Map<String, DataSource> instances = new HashMap<>();
        instances.put("mysql-prod", mockMysql);
        instances.put("postgres-analytics", mockPostgres);
        JDBCProvider provider = createProvider(instances);

        assertSame(mockMysql, provider.get(inputWithConnectionId("mysql-prod")));
        assertSame(mockPostgres, provider.get(inputWithConnectionId("postgres-analytics")));
    }

    @Test
    void testBothConnectionIdAndIntegrationNameNull() {
        JDBCProvider provider = createProvider(Collections.emptyMap());

        JDBCInput input = new JDBCInput();
        // both null
        assertNull(provider.get(input));
    }

    @Test
    void testConfigExceptionDoesNotPropagate() {
        JDBCInstanceConfig instanceConfig = mock(JDBCInstanceConfig.class);
        when(instanceConfig.getJDBCInstances()).thenThrow(new RuntimeException("Config error"));

        JDBCProvider provider = new JDBCProvider(instanceConfig);

        assertNull(provider.get(inputWithConnectionId("anything")));
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
        JDBCProvider provider = createProvider(instances);

        provider.shutdown();

        verify(mockHikari1).close();
        verify(mockHikari2).close();

        assertNull(provider.get(inputWithConnectionId("ds1")));
        assertNull(provider.get(inputWithConnectionId("ds2")));
    }
}
