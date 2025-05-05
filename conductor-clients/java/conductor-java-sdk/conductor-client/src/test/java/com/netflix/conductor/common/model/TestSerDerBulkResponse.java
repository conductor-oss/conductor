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
package com.netflix.conductor.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestSerDerBulkResponse {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void testSerializeDeserialize() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("BulkResponse");
        BulkResponse<?> bulkResponse = objectMapper.readValue(SERVER_JSON, BulkResponse.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(bulkResponse);

        // Check bulk successful results
        assertNotNull(bulkResponse.getBulkSuccessfulResults());
        assertEquals(1, bulkResponse.getBulkSuccessfulResults().size());

        // Check bulk error results
        assertNotNull(bulkResponse.getBulkErrorResults());
        assertEquals(1, bulkResponse.getBulkErrorResults().size());

        String errorKey = bulkResponse.getBulkErrorResults().keySet().iterator().next();
        assertNotNull(errorKey);
        assertNotNull(bulkResponse.getBulkErrorResults().get(errorKey));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(bulkResponse);

        // 4. Compare the JSONs - with special handling for the message field
        // Since the message field is private final without a getter, it won't be in the serialized output
        // We need to compare only the fields that should be preserved
        assertEquals(
                objectMapper.readTree(SERVER_JSON).get("bulkErrorResults"),
                objectMapper.readTree(serializedJson).get("bulkErrorResults"),
                "bulkErrorResults should match"
        );
        assertEquals(
                objectMapper.readTree(SERVER_JSON).get("bulkSuccessfulResults"),
                objectMapper.readTree(serializedJson).get("bulkSuccessfulResults"),
                "bulkSuccessfulResults should match"
        );
    }
}