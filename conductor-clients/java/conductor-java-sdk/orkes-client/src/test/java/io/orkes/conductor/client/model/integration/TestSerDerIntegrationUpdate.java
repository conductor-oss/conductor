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

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

//TODo : this is an inner class in orkes-conductor , so missing from template - fix it
class TestSerDerIntegrationUpdate {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Get JSON template and unmarshal to POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("IntegrationUpdate");
        IntegrationUpdate integrationUpdate = objectMapper.readValue(SERVER_JSON, IntegrationUpdate.class);

        // 2. Assert fields are correctly populated
        assertNotNull(integrationUpdate);

        // Check Category enum
        assertNotNull(integrationUpdate.getCategory());

        // Check Map
        assertNotNull(integrationUpdate.getConfiguration());
        assertFalse(integrationUpdate.getConfiguration().isEmpty());

        // Check String fields
        assertNotNull(integrationUpdate.getDescription());
        assertNotNull(integrationUpdate.getType());

        // Check Boolean field
        assertNotNull(integrationUpdate.getEnabled());

        // 3. Marshal POJO back to JSON
        String serializedJson = objectMapper.writeValueAsString(integrationUpdate);

        // 4. Compare JSONs
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson),
                "Serialized JSON should match original JSON"
        );
    }
}