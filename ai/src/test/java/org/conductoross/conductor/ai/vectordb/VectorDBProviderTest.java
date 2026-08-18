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

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorDBProviderTest {

    /** An ObjectProvider that iterates over the given default instances (empty by default). */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<VectorDB> defaults(VectorDB... instances) {
        ObjectProvider<VectorDB> provider = mock(ObjectProvider.class);
        // VectorDBProvider consumes defaults via forEach; stub it directly since Mockito does not
        // run the real Iterable#forEach default method on a mock.
        doAnswer(
                        inv -> {
                            java.util.function.Consumer<VectorDB> consumer = inv.getArgument(0);
                            List.of(instances).forEach(consumer);
                            return null;
                        })
                .when(provider)
                .forEach(any());
        return provider;
    }

    @Test
    void testEmptyConfigList() {
        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(Collections.emptyMap());

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

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

    @Test
    void constructorPropagatesInstanceConfigFailure() {
        // The provider must not swallow initialization failures into an empty map.
        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenThrow(new IllegalStateException("boom"));

        assertThrows(
                IllegalStateException.class,
                () -> new VectorDBProvider(instanceConfig, defaults()));
    }

    @Test
    void testDefaultInstanceIsMerged() {
        VectorDB defaultCustom = mock(VectorDB.class);
        when(defaultCustom.getName()).thenReturn("default");
        when(defaultCustom.getType()).thenReturn("custom");

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(Collections.emptyMap());

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults(defaultCustom));

        VectorDB result = provider.get("default", mock(TaskContext.class));
        assertNotNull(result);
        assertEquals("custom", result.getType());
    }

    @Test
    void testExplicitInstanceTakesPrecedenceOverDefault() {
        VectorDB explicit = mock(VectorDB.class);
        when(explicit.getName()).thenReturn("default");
        when(explicit.getType()).thenReturn("postgres");

        VectorDB defaultCustom = mock(VectorDB.class);
        when(defaultCustom.getName()).thenReturn("default");
        when(defaultCustom.getType()).thenReturn("custom");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("default", explicit);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults(defaultCustom));

        // The explicitly configured instance wins; putIfAbsent does not overwrite it.
        assertEquals("postgres", provider.get("default", mock(TaskContext.class)).getType());
    }

    @Test
    void testDispose_closesCloseableInstances() throws IOException {
        // A VectorDB that implements Closeable
        CloseableVectorDB closeableDB = mock(CloseableVectorDB.class);
        when(closeableDB.getName()).thenReturn("closeable");
        when(closeableDB.getType()).thenReturn("valkey");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("closeable", closeableDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

        // Invoke dispose (the @PreDestroy method)
        provider.dispose();

        // Verify close was called
        verify(closeableDB).close();
    }

    @Test
    void testDispose_continuesAfterOneCloseThrows() throws IOException {
        // Two closeable instances — first one throws
        CloseableVectorDB failingDB = mock(CloseableVectorDB.class);
        when(failingDB.getName()).thenReturn("failing");
        when(failingDB.getType()).thenReturn("valkey");
        doThrow(new IOException("close failed")).when(failingDB).close();

        CloseableVectorDB healthyDB = mock(CloseableVectorDB.class);
        when(healthyDB.getName()).thenReturn("healthy");
        when(healthyDB.getType()).thenReturn("valkey");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("failing", failingDB);
        instances.put("healthy", healthyDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

        // Should NOT throw even though one close() fails
        assertDoesNotThrow(provider::dispose);

        // Both close() should have been attempted
        verify(failingDB).close();
        verify(healthyDB).close();
    }

    @Test
    void testDispose_clearsMapSoSubsequentLookupReturnsNull() throws IOException {
        // Without clearing the map, get() could hand back an already-closed instance to a caller
        // racing shutdown instead of a clean "not found".
        CloseableVectorDB closeableDB = mock(CloseableVectorDB.class);
        when(closeableDB.getName()).thenReturn("closeable");
        when(closeableDB.getType()).thenReturn("valkey");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("closeable", closeableDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());
        provider.dispose();

        assertNull(provider.get("closeable", mock(TaskContext.class)));
    }

    @Test
    void testDispose_skipsNonCloseableInstances() {
        // A plain VectorDB mock (not Closeable) should not cause errors
        VectorDB plainDB = mock(VectorDB.class);
        when(plainDB.getName()).thenReturn("plain");
        when(plainDB.getType()).thenReturn("postgres");

        Map<String, VectorDB> instances = new HashMap<>();
        instances.put("plain", plainDB);

        VectorDBInstanceConfig instanceConfig = mock(VectorDBInstanceConfig.class);
        when(instanceConfig.getVectorDBInstances()).thenReturn(instances);

        VectorDBProvider provider = new VectorDBProvider(instanceConfig, defaults());

        // dispose should succeed silently
        assertDoesNotThrow(provider::dispose);
    }

    /** Test helper — an abstract class that combines VectorDB and Closeable for mocking. */
    abstract static class CloseableVectorDB extends VectorDB implements Closeable {
        CloseableVectorDB() {
            super("mock", "mock");
        }
    }
}
