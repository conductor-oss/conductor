/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolSpecTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    void selfDescribing_true_roundTrips() throws Exception {
        String json = "{\"name\":\"t\",\"inputSchema\":{},\"selfDescribing\":true}";
        ToolSpec spec = objectMapper.readValue(json, ToolSpec.class);
        assertTrue(spec.isSelfDescribing(), "selfDescribing must survive deserialization");

        String serialized = objectMapper.writeValueAsString(spec);
        ToolSpec roundTripped = objectMapper.readValue(serialized, ToolSpec.class);
        assertTrue(roundTripped.isSelfDescribing());
    }

    @Test
    void selfDescribing_absent_defaultsFalse() throws Exception {
        String json = "{\"name\":\"t\",\"inputSchema\":{}}";
        ToolSpec spec = objectMapper.readValue(json, ToolSpec.class);
        assertFalse(spec.isSelfDescribing(), "absent selfDescribing must default to false");
    }

    @Test
    void selfDescribing_false_explicit_roundTrips() throws Exception {
        String json = "{\"name\":\"t\",\"selfDescribing\":false}";
        ToolSpec spec = objectMapper.readValue(json, ToolSpec.class);
        assertFalse(spec.isSelfDescribing());
    }

    @Test
    void selfDescribing_setterGetter() {
        ToolSpec spec = new ToolSpec();
        spec.setName("tool");
        spec.setInputSchema(Map.of("type", "object"));
        spec.setSelfDescribing(true);
        assertTrue(spec.isSelfDescribing());
        spec.setSelfDescribing(false);
        assertFalse(spec.isSelfDescribing());
    }
}
