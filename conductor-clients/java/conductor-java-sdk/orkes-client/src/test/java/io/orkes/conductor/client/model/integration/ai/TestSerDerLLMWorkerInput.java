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

import java.util.List;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerLLMWorkerInput {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("LLMWorkerInput");
        LLMWorkerInput llmWorkerInput = objectMapper.readValue(SERVER_JSON, LLMWorkerInput.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(llmWorkerInput);
        assertNotNull(llmWorkerInput.getLlmProvider());
        assertNotNull(llmWorkerInput.getModel());
        assertNotNull(llmWorkerInput.getEmbeddingModel());
        assertNotNull(llmWorkerInput.getEmbeddingModelProvider());
        assertNotNull(llmWorkerInput.getPrompt());
        assertEquals(123.456, llmWorkerInput.getTemperature(), 0.001);
        assertEquals(123.456, llmWorkerInput.getTopP(), 0.001);

        // Check the list values
        List<String> stopWords = llmWorkerInput.getStopWords();
        assertNotNull(stopWords);

        // Checking maxTokens and maxResults
        assertEquals(123, llmWorkerInput.getMaxResults());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(llmWorkerInput);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}