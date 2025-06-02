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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerPromptTemplateTestRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Load the SERVER_JSON
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("PromptTemplateTestRequest");

        // 2. Unmarshal SERVER_JSON to SDK POJO
        PromptTemplateTestRequest request = objectMapper.readValue(SERVER_JSON, PromptTemplateTestRequest.class);

        // 3. Assert that fields are correctly populated
        assertNotNull(request);

        // Check string fields
        assertNotNull(request.getLlmProvider());
        assertEquals("sample_llmProvider", request.getLlmProvider());

        assertNotNull(request.getModel());
        assertEquals("sample_model", request.getModel());

        assertNotNull(request.getPrompt());
        assertEquals("sample_prompt", request.getPrompt());

        // Check numeric fields
        assertEquals(123.456, request.getTemperature());
        assertEquals(123.456, request.getTopP());

        // Check map
        assertNotNull(request.getPromptVariables());
        Map<String, Object> expectedVariables = new HashMap<>();
        expectedVariables.put("key", "sample_value");
        assertEquals(expectedVariables, request.getPromptVariables());
        assertEquals("sample_value", request.getPromptVariables().get("key"));

        // Check list
        assertNotNull(request.getStopWords());
        assertEquals(Arrays.asList("sample_stopWords"), request.getStopWords());
        assertEquals(1, request.getStopWords().size());

        // 4. Marshall POJO back to JSON
        String serializedJson = objectMapper.writeValueAsString(request);

        // 5. Compare JSONs to ensure nothing is lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}