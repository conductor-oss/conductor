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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Compile-time schemas for the explicitly supported OCG query and graph tools. */
final class OcgToolCatalog {

    private static final String RESOURCE = "/ocg-tool-catalog.json";
    private static final Map<String, Definition> DEFINITIONS = load();

    private OcgToolCatalog() {}

    static Definition get(String name) {
        return DEFINITIONS.get(name);
    }

    static List<Map.Entry<String, Definition>> entries() {
        return List.copyOf(DEFINITIONS.entrySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Definition> load() {
        ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
        try (InputStream input = OcgToolCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            List<Map<String, Object>> raw =
                    mapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Definition> definitions = new LinkedHashMap<>();
            for (Map<String, Object> item : raw) {
                String name = String.valueOf(item.get("name"));
                definitions.put(
                        name,
                        new Definition(
                                String.valueOf(item.get("description")),
                                (Map<String, Object>) item.get("inputSchema")));
            }
            return Collections.unmodifiableMap(definitions);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load " + RESOURCE, e);
        }
    }

    record Definition(String description, Map<String, Object> inputSchema) {}
}
