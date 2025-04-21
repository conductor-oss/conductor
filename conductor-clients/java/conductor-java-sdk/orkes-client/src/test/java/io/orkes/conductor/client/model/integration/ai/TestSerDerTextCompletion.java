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

public class TestSerDerTextCompletion {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("TextCompletion");

        TextCompletion textCompletion = objectMapper.readValue(SERVER_JSON, TextCompletion.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(textCompletion);
        // Add specific assertions for fields inherited from LLMWorkerInput
        // For example, if LLMWorkerInput has fields like 'model', 'prompt', etc.
        // assertNotNull(textCompletion.getModel());
        // assertEquals("sample_model", textCompletion.getModel());
        // If there are maps, lists, or enums, verify them here

        // 3. Marshal this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(textCompletion);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}