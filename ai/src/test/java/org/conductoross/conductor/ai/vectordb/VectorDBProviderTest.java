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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorDBProviderTest {

    @Test
    void testEmptyConfigList() {
        VectorDBProvider provider = new VectorDBProvider(List.of());

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("pgvectordb", mockContext);

        assertNull(result);
    }

    @Test
    void testGetRegisteredVectorDB() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getType()).thenReturn("pgvectordb");

        @SuppressWarnings("unchecked")
        VectorDBConfig<VectorDB> mockConfig = mock(VectorDBConfig.class);
        when(mockConfig.get()).thenReturn(mockVectorDB);

        VectorDBProvider provider = new VectorDBProvider(List.of(mockConfig));

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("pgvectordb", mockContext);

        assertNotNull(result);
        assertEquals("pgvectordb", result.getType());
    }

    @Test
    void testGetUnregisteredVectorDB() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getType()).thenReturn("pgvectordb");

        @SuppressWarnings("unchecked")
        VectorDBConfig<VectorDB> mockConfig = mock(VectorDBConfig.class);
        when(mockConfig.get()).thenReturn(mockVectorDB);

        VectorDBProvider provider = new VectorDBProvider(List.of(mockConfig));

        TaskContext mockContext = mock(TaskContext.class);
        VectorDB result = provider.get("unknown", mockContext);

        assertNull(result);
    }

    @Test
    void testMultipleVectorDBs() {
        VectorDB mockPgVectorDB = mock(VectorDB.class);
        when(mockPgVectorDB.getType()).thenReturn("pgvectordb");

        VectorDB mockMongoVectorDB = mock(VectorDB.class);
        when(mockMongoVectorDB.getType()).thenReturn("mongovectordb");

        @SuppressWarnings("unchecked")
        VectorDBConfig<VectorDB> pgConfig = mock(VectorDBConfig.class);
        when(pgConfig.get()).thenReturn(mockPgVectorDB);

        @SuppressWarnings("unchecked")
        VectorDBConfig<VectorDB> mongoConfig = mock(VectorDBConfig.class);
        when(mongoConfig.get()).thenReturn(mockMongoVectorDB);

        VectorDBProvider provider = new VectorDBProvider(List.of(pgConfig, mongoConfig));

        TaskContext mockContext = mock(TaskContext.class);

        assertEquals("pgvectordb", provider.get("pgvectordb", mockContext).getType());
        assertEquals("mongovectordb", provider.get("mongovectordb", mockContext).getType());
    }

    @Test
    void testGetWithNullContext() {
        VectorDB mockVectorDB = mock(VectorDB.class);
        when(mockVectorDB.getType()).thenReturn("pgvectordb");

        @SuppressWarnings("unchecked")
        VectorDBConfig<VectorDB> mockConfig = mock(VectorDBConfig.class);
        when(mockConfig.get()).thenReturn(mockVectorDB);

        VectorDBProvider provider = new VectorDBProvider(List.of(mockConfig));

        // Should not throw even with null context
        VectorDB result = provider.get("pgvectordb", null);
        assertNotNull(result);
    }
}
