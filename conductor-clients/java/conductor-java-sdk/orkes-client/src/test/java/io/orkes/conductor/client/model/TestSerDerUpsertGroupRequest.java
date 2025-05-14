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
package io.orkes.conductor.client.model;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.model.UpsertGroupRequest.RolesEnum;
import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerUpsertGroupRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("UpsertGroupRequest");
        UpsertGroupRequest upsertGroupRequest = objectMapper.readValue(SERVER_JSON, UpsertGroupRequest.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(upsertGroupRequest);

        // Check description field
        String description = upsertGroupRequest.getDescription();
        assertNotNull(description);
        assertEquals("sample_description", description);

        // Check roles list and enum values
        List<RolesEnum> roles = upsertGroupRequest.getRoles();
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals(RolesEnum.USER, roles.get(0));

        // Check defaultAccess map
        Map<String, List<String>> defaultAccess = upsertGroupRequest.getDefaultAccess();
        assertNotNull(defaultAccess);
        assertEquals(1, defaultAccess.size());

        // Get the first key-value pair
        String key = defaultAccess.keySet().iterator().next();
        List<String> values = defaultAccess.get(key);

        assertNotNull(key);
        assertNotNull(values);
        assertEquals(1, values.size());
        assertEquals("CREATE", values.get(0));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(upsertGroupRequest);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}