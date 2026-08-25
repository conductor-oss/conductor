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

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JsonSchemaValidator {

    /**
     * Dialect used when a schema does not declare one through $schema. Draft 2020-12 matches what
     * SpecVersionDetector defaulted to for undeclared schemas.
     */
    private static final SpecificationVersion DEFAULT_DIALECT = SpecificationVersion.DRAFT_2020_12;

    private final ObjectMapper mapper;

    @SneakyThrows
    public Schema getJsonSchema(String schemaContent) {
        JsonNode jsonNode = mapper.readTree(schemaContent);
        SpecificationVersion dialect =
                SpecificationVersion.fromSchemaNode(jsonNode).orElse(DEFAULT_DIALECT);
        return SchemaRegistry.withDefaultDialect(dialect).getSchema(jsonNode);
    }

    public List<Error> validate(String schemaContent, Map<String, Object> body) {
        Schema schema = getJsonSchema(schemaContent);
        JsonNode node = getJsonNode(body);
        return schema.validate(node);
    }

    @SneakyThrows
    private JsonNode getJsonNode(Map<String, Object> body) {
        return mapper.valueToTree(body);
    }
}
