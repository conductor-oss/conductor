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

import org.conductoross.conductor.ai.models.IndexedDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorDBsTest {

    private VectorDBProvider mockProvider;
    private VectorDB mockVectorDB;
    private VectorDBs vectorDBs;
    private TaskContext mockContext;

    @BeforeEach
    void setUp() {
        mockProvider = mock(VectorDBProvider.class);
        mockVectorDB = mock(VectorDB.class);
        mockContext = mock(TaskContext.class);
        vectorDBs = new VectorDBs(mockProvider);
    }

    @Test
    void testStoreEmbeddings_success() {
        when(mockProvider.get("postgres-prod", mockContext)).thenReturn(mockVectorDB);
        when(mockVectorDB.updateEmbeddings(
                        eq("index1"),
                        eq("namespace1"),
                        eq("sample text"),
                        eq("parent-doc-1"),
                        eq("doc-1"),
                        anyList(),
                        anyMap()))
                .thenReturn(1);

        int result =
                vectorDBs.storeEmbeddings(
                        "postgres-prod",
                        mockContext,
                        "index1",
                        "namespace1",
                        "sample text",
                        "parent-doc-1",
                        "doc-1",
                        java.util.List.of(0.1f, 0.2f, 0.3f),
                        java.util.Map.of("key", "value"));

        assertEquals(1, result);
        verify(mockVectorDB)
                .updateEmbeddings(
                        "index1",
                        "namespace1",
                        "sample text",
                        "parent-doc-1",
                        "doc-1",
                        java.util.List.of(0.1f, 0.2f, 0.3f),
                        java.util.Map.of("key", "value"));
    }

    @Test
    void testStoreEmbeddings_vectorDBNotFound() {
        when(mockProvider.get("unknown", mockContext)).thenReturn(null);

        NonRetryableException ex =
                assertThrows(
                        NonRetryableException.class,
                        () ->
                                vectorDBs.storeEmbeddings(
                                        "unknown",
                                        mockContext,
                                        "index1",
                                        "namespace1",
                                        "text",
                                        null,
                                        "doc-1",
                                        java.util.List.of(0.1f),
                                        null));

        assertEquals("VectorDB not found: unknown", ex.getMessage());
    }

    @Test
    void testSearchEmbeddings_success() {
        java.util.List<IndexedDoc> expectedResults = java.util.List.of(new IndexedDoc());

        when(mockProvider.get("mongodb-prod", mockContext)).thenReturn(mockVectorDB);
        when(mockVectorDB.search(eq("index1"), eq("namespace1"), anyList(), eq(10)))
                .thenReturn(expectedResults);

        java.util.List<IndexedDoc> result =
                vectorDBs.searchEmbeddings(
                        "mongodb-prod",
                        mockContext,
                        "index1",
                        "namespace1",
                        java.util.List.of(0.1f, 0.2f, 0.3f),
                        10);

        assertEquals(expectedResults, result);
        verify(mockVectorDB)
                .search("index1", "namespace1", java.util.List.of(0.1f, 0.2f, 0.3f), 10);
    }

    @Test
    void testSearchEmbeddings_vectorDBNotFound() {
        when(mockProvider.get("unknown", mockContext)).thenReturn(null);

        NonRetryableException ex =
                assertThrows(
                        NonRetryableException.class,
                        () ->
                                vectorDBs.searchEmbeddings(
                                        "unknown",
                                        mockContext,
                                        "index1",
                                        "namespace1",
                                        java.util.List.of(0.1f),
                                        10));

        assertEquals("VectorDB not found: unknown", ex.getMessage());
    }

    @Test
    void testStoreEmbeddings_nullMetadata() {
        when(mockProvider.get("postgres-prod", mockContext)).thenReturn(mockVectorDB);
        when(mockVectorDB.updateEmbeddings(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyList(),
                        isNull()))
                .thenReturn(1);

        int result =
                vectorDBs.storeEmbeddings(
                        "postgres-prod",
                        mockContext,
                        "index1",
                        "namespace1",
                        "text",
                        null,
                        "doc-1",
                        java.util.List.of(0.1f),
                        null);

        assertEquals(1, result);
    }
}
