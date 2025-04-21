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

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerIndexedDoc {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("IndexedDoc");
        IndexedDoc indexedDoc = objectMapper.readValue(SERVER_JSON, IndexedDoc.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(indexedDoc);
        assertNotNull(indexedDoc.getDocId());
        assertNotNull(indexedDoc.getParentDocId());
        assertNotNull(indexedDoc.getText());
        assertEquals(123.456, indexedDoc.getScore(), 0.001);

        // Check metadata map
        assertNotNull(indexedDoc.getMetadata());

        // If the template JSON has specific metadata entries, test them here
        // For example:
        // assertEquals("value1", indexedDoc.getMetadata().get("key1"));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(indexedDoc);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }

    @Test
    public void testCustomValues() throws Exception {
        // Create a POJO with custom values
        String docId = "doc123";
        String parentDocId = "parent456";
        String text = "This is a test document";
        double score = 0.95;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", 123);
        metadata.put("key3", true);

        IndexedDoc indexedDoc = new IndexedDoc(docId, parentDocId, text, score);
        indexedDoc.setMetadata(metadata);

        // Serialize the POJO
        String serializedJson = objectMapper.writeValueAsString(indexedDoc);

        // Deserialize back to POJO
        IndexedDoc deserializedDoc = objectMapper.readValue(serializedJson, IndexedDoc.class);

        // Verify all fields match
        assertEquals(docId, deserializedDoc.getDocId());
        assertEquals(parentDocId, deserializedDoc.getParentDocId());
        assertEquals(text, deserializedDoc.getText());
        assertEquals(score, deserializedDoc.getScore(), 0.001);

        // Verify metadata
        assertEquals(metadata.size(), deserializedDoc.getMetadata().size());
        assertEquals("value1", deserializedDoc.getMetadata().get("key1"));
        assertEquals(123, deserializedDoc.getMetadata().get("key2"));
        assertEquals(true, deserializedDoc.getMetadata().get("key3"));

        // Verify JSON equivalence
        assertEquals(objectMapper.readTree(serializedJson),
                objectMapper.readTree(objectMapper.writeValueAsString(deserializedDoc)));
    }
}