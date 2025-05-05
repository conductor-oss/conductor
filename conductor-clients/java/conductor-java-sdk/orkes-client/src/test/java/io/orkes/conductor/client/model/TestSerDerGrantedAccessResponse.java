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

//todo - adding Missing fields in GrantedAccess will pass this test
public class TestSerDerGrantedAccessResponse {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("GrantedAccessResponse");
        GrantedAccessResponse grantedAccessResponse = objectMapper.readValue(SERVER_JSON, GrantedAccessResponse.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(grantedAccessResponse, "Deserialized object should not be null");
        assertNotNull(grantedAccessResponse.getGrantedAccess(), "grantedAccess list should not be null");
        assertEquals(1, grantedAccessResponse.getGrantedAccess().size(), "grantedAccess list should have 1 item");

        // Assert the GrantedAccess item properties if needed
        GrantedAccess grantedAccess = grantedAccessResponse.getGrantedAccess().get(0);
        assertNotNull(grantedAccess, "GrantedAccess item should not be null");

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(grantedAccessResponse);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson),
                "Serialized JSON should match the original JSON"
        );
    }
}