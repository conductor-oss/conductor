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
package com.netflix.conductor.common.metadata;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerSchemaDef {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerDer() throws IOException {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("SchemaDef");
        SchemaDef schemaDef = objectMapper.readValue(SERVER_JSON, SchemaDef.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(schemaDef);
        assertEquals("sample_name", schemaDef.getName());
        assertEquals(1, schemaDef.getVersion());
        assertEquals(SchemaDef.Type.JSON, schemaDef.getType());
        assertNotNull(schemaDef.getData());
        assertEquals(1, schemaDef.getData().size());
        assertEquals("sample_value", schemaDef.getData().get("sample_key"));
        assertEquals("sample_externalRef", schemaDef.getExternalRef());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(schemaDef);

        // 4. Compare the JSONs - nothing should be lost
        // Using JsonNode comparison which ignores field order
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson),
                "The serialized and deserialized JSONs should be equivalent regardless of field order"
        );
    }
}