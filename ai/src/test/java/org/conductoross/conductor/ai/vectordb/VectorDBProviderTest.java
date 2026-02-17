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
package org.conductoross.conductor.ai.vectordb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorDBProviderTest {

    @Test
    void testEmptyConfigList() {
        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(Collections.emptyMap());

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("postgres-prod", mockContext);

        assertNull(result);
    }

    @Test
    void testGetRegisteredVectorDB() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getName()).thenReturn("postgres-prod");
        when(mockVectorDB.getType()).thenReturn("postgres");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("postgres-prod", mockVectorDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("postgres-prod", mockContext);

        assertNotNull(result);
        assertEquals("postgres-prod", result.getName());
        assertEquals("postgres", result.getType());
    }

    @Test
    void testGetUnregisteredVectorDB() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getName()).thenReturn("postgres-prod");
        when(mockVectorDB.getType()).thenReturn("postgres");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("postgres-prod", mockVectorDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("unknown", mockContext);

        assertNull(result);
    }

    @Test
    void testMultipleVectorDBs() {
        VectorDB mockPgVectorDB = mock(VectorDB.class);
        when(mockPgVectorDB.getName()).thenReturn("postgres-prod");
        when(mockPgVectorDB.getType()).thenReturn("postgres");

        VectorDB mockMongoVectorDB = mock(VectorDB.class);
        when(mockMongoVectorDB.getName()).thenReturn("mongo-embeddings");
        when(mockMongoVectorDB.getType()).thenReturn("mongodb");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("postgres-prod", mockPgVectorDB);
        instances.put("mongo-embeddings", mockMongoVectorDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        TaskContext mockContext = mock(TaskContext.class);

        assertEquals("postgres", provider.get("postgres-prod", mockContext).getType());
        assertEquals("mongodb", provider.get("mongo-embeddings", mockContext).getType());
    }

    @Test
    void testGetWithNullContext() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getName()).thenReturn("postgres-prod");
        when(mockVectorDB.getType()).thenReturn("postgres");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("postgres-prod", mockVectorDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        // Should not throw even with null context
        VectorDB result = provider.get("postgres-prod", null);
        assertNotNull(result);
    }

    @Test
    void testMultipleInstancesOfSameType() {
        VectorDB mockPgProd = mock(VectorDB.class);
        when(mockPgProd.getName()).thenReturn("postgres-prod");
        when(mockPgProd.getType()).thenReturn("postgres");

        VectorDB mockPgDev = mock(VectorDB.class);
        when(mockPgDev.getName()).thenReturn("postgres-dev");
        when(mockPgDev.getType()).thenReturn("postgres");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("postgres-prod", mockPgProd);
        instances.put("postgres-dev", mockPgDev);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig);

        TaskContext mockContext = mock(TaskContext.class);

        // Both instances should be accessible by their names
        VectorDB prodDb = provider.get("postgres-prod", mockContext);
        VectorDB devDb = provider.get("postgres-dev", mockContext);

        assertNotNull(prodDb);
        assertNotNull(devDb);
        assertEquals("postgres-prod", prodDb.getName());
        assertEquals("postgres-dev", devDb.getName());
        assertEquals("postgres", prodDb.getType());
        assertEquals("postgres", devDb.getType());
    }
}
