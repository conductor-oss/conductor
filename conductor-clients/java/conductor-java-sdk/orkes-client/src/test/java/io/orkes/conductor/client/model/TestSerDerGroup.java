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

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerGroup {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("Group");
        Group group = objectMapper.readValue(SERVER_JSON, Group.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(group);

        // Check id
        assertNotNull(group.getId());
        assertEquals("sample_id", group.getId());

        // Check description
        assertNotNull(group.getDescription());
        assertEquals("sample_description", group.getDescription());

        // Check defaultAccess (Map<String, List<String>>)
        assertNotNull(group.getDefaultAccess());
        assertEquals(1, group.getDefaultAccess().size());

        String mapKey = group.getDefaultAccess().keySet().iterator().next();
        assertNotNull(mapKey);

        assertNotNull(group.getDefaultAccess().get(mapKey));
        assertEquals(1, group.getDefaultAccess().get(mapKey).size());
        assertNotNull(group.getDefaultAccess().get(mapKey).get(0));

        // Check roles (List<Role>)
        assertNotNull(group.getRoles());
        assertEquals(1, group.getRoles().size());

        Role role = group.getRoles().get(0);
        assertNotNull(role);

        // 3. Marshal this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(group);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}