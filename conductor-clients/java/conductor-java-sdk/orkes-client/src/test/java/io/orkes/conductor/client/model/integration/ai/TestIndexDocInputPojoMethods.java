/*
 * Copyright 2025 Conductor Authors.
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
package io.orkes.conductor.client.model.integration.ai;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestIndexDocInputPojoMethods {

    @Test
    public void testNoArgsConstructor() {
        IndexDocInput input = new IndexDocInput();
        assertNull(input.getLlmProvider());
        assertNull(input.getModel());
        assertNull(input.getEmbeddingModelProvider());
        assertNull(input.getEmbeddingModel());
        assertNull(input.getVectorDB());
        assertNull(input.getText());
        assertNull(input.getDocId());
        assertNull(input.getUrl());
        assertNull(input.getMediaType());
        assertNull(input.getNamespace());
        assertNull(input.getIndex());
        assertEquals(12000, input.getChunkSize());
        assertEquals(400, input.getChunkOverlap());
        assertNull(input.getMetadata());
        assertNull(input.getDimensions());
    }

    @Test
    public void testAllArgsConstructor() {
        String llmProvider = "openai";
        String model = "gpt-4";
        String embeddingModelProvider = "openai";
        String embeddingModel = "text-embedding-ada-002";
        String vectorDB = "pinecone";
        String text = "Sample text";
        String docId = "doc123";
        String url = "https://example.com";
        String mediaType = "text/plain";
        String namespace = "test-namespace";
        String index = "test-index";
        int chunkSize = 1000;
        int chunkOverlap = 200;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        Integer dimensions = 1536;

        IndexDocInput input = new IndexDocInput(
                llmProvider, model, embeddingModelProvider, embeddingModel,
                vectorDB, text, docId, url, mediaType, namespace, index,
                chunkSize, chunkOverlap, metadata, dimensions);

        assertEquals(llmProvider, input.getLlmProvider());
        assertEquals(model, input.getModel());
        assertEquals(embeddingModelProvider, input.getEmbeddingModelProvider());
        assertEquals(embeddingModel, input.getEmbeddingModel());
        assertEquals(vectorDB, input.getVectorDB());
        assertEquals(text, input.getText());
        assertEquals(docId, input.getDocId());
        assertEquals(url, input.getUrl());
        assertEquals(mediaType, input.getMediaType());
        assertEquals(namespace, input.getNamespace());
        assertEquals(index, input.getIndex());
        assertEquals(chunkSize, input.getChunkSize());
        assertEquals(chunkOverlap, input.getChunkOverlap());
        assertEquals(metadata, input.getMetadata());
        assertEquals(dimensions, input.getDimensions());
    }

    @Test
    public void testGetNamespaceWithNullNamespace() {
        IndexDocInput input = new IndexDocInput();
        input.setDocId("doc123");
        assertEquals("doc123", input.getNamespace());
    }

    @Test
    public void testGetNamespaceWithNonNullNamespace() {
        IndexDocInput input = new IndexDocInput();
        input.setDocId("doc123");
        input.setNamespace("test-namespace");
        assertEquals("test-namespace", input.getNamespace());
    }

    @Test
    public void testGetChunkSizeWithZeroValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkSize(0);
        assertEquals(12000, input.getChunkSize());
    }

    @Test
    public void testGetChunkSizeWithNegativeValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkSize(-100);
        assertEquals(12000, input.getChunkSize());
    }

    @Test
    public void testGetChunkSizeWithPositiveValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkSize(2000);
        assertEquals(2000, input.getChunkSize());
    }

    @Test
    public void testGetChunkOverlapWithZeroValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkOverlap(0);
        assertEquals(400, input.getChunkOverlap());
    }

    @Test
    public void testGetChunkOverlapWithNegativeValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkOverlap(-50);
        assertEquals(400, input.getChunkOverlap());
    }

    @Test
    public void testGetChunkOverlapWithPositiveValue() {
        IndexDocInput input = new IndexDocInput();
        input.setChunkOverlap(300);
        assertEquals(300, input.getChunkOverlap());
    }

    @Test
    public void testSettersAndGetters() {
        IndexDocInput input = new IndexDocInput();

        input.setLlmProvider("openai");
        input.setModel("gpt-4");
        input.setEmbeddingModelProvider("openai");
        input.setEmbeddingModel("text-embedding-ada-002");
        input.setVectorDB("pinecone");
        input.setText("Sample text");
        input.setDocId("doc123");
        input.setUrl("https://example.com");
        input.setMediaType("text/plain");
        input.setNamespace("test-namespace");
        input.setIndex("test-index");
        input.setChunkSize(1000);
        input.setChunkOverlap(200);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        input.setMetadata(metadata);
        input.setDimensions(1536);

        assertEquals("openai", input.getLlmProvider());
        assertEquals("gpt-4", input.getModel());
        assertEquals("openai", input.getEmbeddingModelProvider());
        assertEquals("text-embedding-ada-002", input.getEmbeddingModel());
        assertEquals("pinecone", input.getVectorDB());
        assertEquals("Sample text", input.getText());
        assertEquals("doc123", input.getDocId());
        assertEquals("https://example.com", input.getUrl());
        assertEquals("text/plain", input.getMediaType());
        assertEquals("test-namespace", input.getNamespace());
        assertEquals("test-index", input.getIndex());
        assertEquals(1000, input.getChunkSize());
        assertEquals(200, input.getChunkOverlap());
        assertEquals(metadata, input.getMetadata());
        assertEquals(1536, input.getDimensions());
    }

    @Test
    public void testEqualsAndHashCode() {
        IndexDocInput input1 = new IndexDocInput();
        input1.setLlmProvider("openai");
        input1.setDocId("doc123");

        IndexDocInput input2 = new IndexDocInput();
        input2.setLlmProvider("openai");
        input2.setDocId("doc123");

        IndexDocInput input3 = new IndexDocInput();
        input3.setLlmProvider("anthropic");
        input3.setDocId("doc456");

        // Test equals
        assertTrue(input1.equals(input2));
        assertFalse(input1.equals(input3));

        // Test hashCode
        assertEquals(input1.hashCode(), input2.hashCode());
    }

    @Test
    public void testToString() {
        IndexDocInput input = new IndexDocInput();
        input.setLlmProvider("openai");
        input.setDocId("doc123");

        String toString = input.toString();
        assertTrue(toString.contains("openai"));
        assertTrue(toString.contains("doc123"));
    }
}