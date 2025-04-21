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

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;


//TODo : this is an inner class in orkes-conductor , so missing from template - fix it
public class TestSerDerIntegrationApiUpdate {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerDerIntegrationApiUpdate() throws IOException {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("IntegrationApiUpdate");
        IntegrationApiUpdate integrationApiUpdate = objectMapper.readValue(SERVER_JSON, IntegrationApiUpdate.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(integrationApiUpdate);

        // Check Map configuration
        assertNotNull(integrationApiUpdate.getConfiguration());
        assertEquals(1, integrationApiUpdate.getConfiguration().size());
        assertTrue(integrationApiUpdate.getConfiguration().containsKey("key"));
        assertNotNull(integrationApiUpdate.getConfiguration().get("key"));

        // Check String description
        assertNotNull(integrationApiUpdate.getDescription());
        assertEquals("sample_description", integrationApiUpdate.getDescription());

        // Check Boolean enabled
        assertNotNull(integrationApiUpdate.getEnabled());
        assertTrue(integrationApiUpdate.getEnabled());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(integrationApiUpdate);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}