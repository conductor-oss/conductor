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

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerLLMResponse {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("LLMResponse");
        LLMResponse llmResponse = objectMapper.readValue(SERVER_JSON, LLMResponse.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(llmResponse);
        assertNotNull(llmResponse.getResult());
        assertNotNull(llmResponse.getFinishReason());
        assertEquals(llmResponse.getTokenUsed(), llmResponse.getTokenUsed()); // Verify tokenUsed is preserved

        // If result is a map or list, add specific assertions
        // E.g., if result is a map
        // assertTrue(llmResponse.getResult() instanceof Map);
        // Map<String, Object> resultMap = (Map<String, Object>) llmResponse.getResult();
        // assertFalse(resultMap.isEmpty());

        // If finishReason is an enum, verify it's a valid enum value
        // assertTrue(Arrays.asList(FinishReason.values()).contains(llmResponse.getFinishReason()));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(llmResponse);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}