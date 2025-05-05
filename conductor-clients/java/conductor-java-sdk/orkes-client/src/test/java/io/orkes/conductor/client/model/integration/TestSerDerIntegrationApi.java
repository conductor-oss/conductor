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

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSerDerIntegrationApi {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("IntegrationApi");
        IntegrationApi integrationApi = objectMapper.readValue(SERVER_JSON, IntegrationApi.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(integrationApi);
        assertEquals("sample_api", integrationApi.getApi());

        // Check Map
        assertNotNull(integrationApi.getConfiguration());
        assertTrue(integrationApi.getConfiguration().containsKey("api_key"));
        assertEquals("sample_Object", integrationApi.getConfiguration().get("api_key"));

        assertEquals("sample_description", integrationApi.getDescription());
        assertEquals(true, integrationApi.getEnabled());
        assertEquals("sample_integrationName", integrationApi.getIntegrationName());

        // Check List
        assertNotNull(integrationApi.getTags());
        assertEquals(1, integrationApi.getTags().size());
        TagObject tagObject = integrationApi.getTags().get(0);
        assertNotNull(tagObject);

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(integrationApi);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readValue(SERVER_JSON, IntegrationApi.class),
                objectMapper.readValue(serializedJson, IntegrationApi.class)
        );
    }
}