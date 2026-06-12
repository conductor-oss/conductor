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
package org.conductoross.conductor.common;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@Component
@RequiredArgsConstructor
public class JsonSchemaValidator {

    private final ObjectMapper mapper;

    @SneakyThrows
    public JsonSchema getJsonSchema(String schemaContent) {
        JsonNode jsonNode = mapper.readTree(schemaContent);
        JsonSchemaFactory factory =
                JsonSchemaFactory.getInstance(SpecVersionDetector.detect(jsonNode));
        return factory.getSchema(jsonNode);
    }

    public Set<ValidationMessage> validate(String schemaContent, Map<String, Object> body) {
        JsonSchema schema = getJsonSchema(schemaContent);
        schema.initializeValidators();
        JsonNode node = getJsonNode(body);
        return schema.validate(node);
    }

    @SneakyThrows
    private JsonNode getJsonNode(Map<String, Object> body) {
        return mapper.valueToTree(body);
    }
}
