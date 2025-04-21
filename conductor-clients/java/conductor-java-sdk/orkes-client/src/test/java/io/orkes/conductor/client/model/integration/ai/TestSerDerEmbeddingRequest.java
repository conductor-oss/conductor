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

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerEmbeddingRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("EmbeddingRequest");

        EmbeddingRequest embeddingRequest = objectMapper.readValue(SERVER_JSON, EmbeddingRequest.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(embeddingRequest);
        assertNotNull(embeddingRequest.getLlmProvider());
        assertNotNull(embeddingRequest.getModel());
        assertNotNull(embeddingRequest.getText());
        assertNotNull(embeddingRequest.getDimensions());

        assertEquals(embeddingRequest.getLlmProvider(), objectMapper.readTree(SERVER_JSON).get("llmProvider").asText());
        assertEquals(embeddingRequest.getModel(), objectMapper.readTree(SERVER_JSON).get("model").asText());
        assertEquals(embeddingRequest.getText(), objectMapper.readTree(SERVER_JSON).get("text").asText());
        assertEquals(embeddingRequest.getDimensions(), objectMapper.readTree(SERVER_JSON).get("dimensions").asInt());

        // 3. Marshall this POJO to JSON again
        String reSerialized = objectMapper.writeValueAsString(embeddingRequest);

        // 4. Compare the JSONs - nothing should be lost
        JsonNode originalJson = objectMapper.readTree(SERVER_JSON);
        JsonNode reSerializedJson = objectMapper.readTree(reSerialized);

        assertEquals(originalJson.size(), reSerializedJson.size());
        assertEquals(originalJson.get("llmProvider"), reSerializedJson.get("llmProvider"));
        assertEquals(originalJson.get("model"), reSerializedJson.get("model"));
        assertEquals(originalJson.get("text"), reSerializedJson.get("text"));
        assertEquals(originalJson.get("dimensions"), reSerializedJson.get("dimensions"));
    }
}