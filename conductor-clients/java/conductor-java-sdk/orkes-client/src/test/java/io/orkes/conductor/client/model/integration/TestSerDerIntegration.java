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
package io.orkes.conductor.client.model.integration;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// todo - add missing fields in sdk pojo should pass this test
public class TestSerDerIntegration {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("Integration");
        Integration integration = objectMapper.readValue(SERVER_JSON, Integration.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(integration);

        // Check enum field
        assertNotNull(integration.getCategory());

        // Check Map field
        Map<String, Object> configuration = integration.getConfiguration();
        assertNotNull(configuration);
        assertTrue(configuration.size() > 0);
        assertTrue(configuration.containsKey("${ConfigKey}"));

        // Check String fields
        assertEquals("sample_description", integration.getDescription());
        assertEquals("sample_name", integration.getName());
        assertEquals("sample_type", integration.getType());

        // Check Long fields
        assertEquals(123L, integration.getModelsCount());

        // Check Boolean field
        assertEquals(true, integration.getEnabled());

        // Check List field
        List<TagObject> tags = integration.getTags();
        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertNotNull(tags.get(0));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(integration);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readValue(SERVER_JSON, Integration.class),
                objectMapper.readValue(serializedJson, Integration.class)
        );
    }
}