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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Converts JSON-Schema maps produced by {@code AgentConfig} output-type declarations into the
 * compact, human-readable text forms embedded in LLM system prompts.
 */
class JsonSchemaTextConverter {

    private JsonSchemaTextConverter() {}

    /**
     * Format a Java object as Python dict repr: {'key': 'value', ...} This matches Python's str()
     * on a dict for system prompt embedding.
     */
    /**
     * Convert an inlined JSON Schema map to a compact Python-style type string.
     *
     * <p>JSON Schema's structural keywords ({@code type}, {@code items}, {@code properties}) are
     * translated into idiomatic Python type notation:
     *
     * <ul>
     *   <li>{@code {"type":"string"}} → {@code str}
     *   <li>{@code {"type":"integer"}} → {@code int}
     *   <li>{@code {"type":"number"}} → {@code float}
     *   <li>{@code {"type":"boolean"}} → {@code bool}
     *   <li>{@code {"type":"array","items":{...}}} → {@code [item_type]}
     *   <li>{@code {"type":"object","properties":{...}}} → {@code {key: type, ...}}
     * </ul>
     *
     * This avoids passing raw JSON Schema keywords like {@code items} and {@code title} to the LLM,
     * which would otherwise interpret them as data field names.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String simplifySchema(Map<String, Object> schema) {
        return simplifyNode(schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String simplifyNode(Object node) {
        if (!(node instanceof Map)) {
            return String.valueOf(node);
        }
        Map<String, Object> m = (Map<String, Object>) node;
        String type = m.containsKey("type") ? String.valueOf(m.get("type")) : null;

        if ("array".equals(type)) {
            Object items = m.get("items");
            if (items instanceof Map) {
                return "[" + simplifyNode(items) + "]";
            }
            return "list";
        }

        if ("object".equals(type) || m.containsKey("properties")) {
            Object propsObj = m.get("properties");
            if (propsObj instanceof Map) {
                Map<String, Object> props = (Map<String, Object>) propsObj;
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append("'")
                            .append(entry.getKey())
                            .append("': ")
                            .append(simplifyNode(entry.getValue()));
                }
                sb.append("}");
                return sb.toString();
            }
            return "object";
        }

        if ("string".equals(type)) return "str";
        if ("integer".equals(type)) return "int";
        if ("number".equals(type)) return "float";
        if ("boolean".equals(type)) return "bool";
        if ("null".equals(type)) return "None";

        // anyOf: Pydantic uses this for Optional[T] → [T, null] → render as "T | None"
        if (m.containsKey("anyOf")) {
            List<Object> variants = (List<Object>) m.get("anyOf");
            List<String> parts = new ArrayList<>();
            for (Object v : variants) {
                String s = simplifyNode(v);
                if (!"None".equals(s)) parts.add(s); // put None last
            }
            parts.add("None");
            // deduplicate
            LinkedHashSet<String> unique = new LinkedHashSet<>(parts);
            return String.join(" | ", unique);
        }

        // enum: render as a list of allowed values
        if (m.containsKey("enum")) {
            return String.valueOf(m.get("enum"));
        }

        // Fallback: render as dict
        if (m.containsKey("properties")) return simplifyNode(m);
        return type != null ? type : "any";
    }

    /**
     * Recursively inline all {@code $ref} references in a JSON Schema map.
     *
     * <p>Pydantic's {@code model_json_schema()} produces schemas with a top-level {@code $defs}
     * section and {@code $ref: "#/$defs/Foo"} pointers inside {@code properties}. This method
     * resolves every {@code $ref} by substituting the referenced definition in-place, so the
     * resulting map contains no unresolved references and can be understood by an LLM without
     * needing JSON-Schema-aware tooling.
     *
     * @param schema the raw JSON Schema map (may contain {@code $defs} and {@code $ref})
     * @return a new map with all references fully inlined
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, Object> inlineRefs(Map<String, Object> schema) {
        Map<String, Object> defs =
                schema.containsKey("$defs")
                        ? (Map<String, Object>) schema.get("$defs")
                        : Collections.emptyMap();
        return (Map<String, Object>) resolveNode(schema, defs);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolveNode(Object node, Map<String, Object> defs) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            // Resolve $ref first
            if (m.containsKey("$ref")) {
                String ref = (String) m.get("$ref");
                // Format: "#/$defs/TypeName"
                if (ref != null && ref.startsWith("#/$defs/")) {
                    String typeName = ref.substring("#/$defs/".length());
                    Object definition = defs.get(typeName);
                    if (definition instanceof Map<?, ?>) {
                        // Recursively resolve the referenced definition
                        return resolveNode(definition, defs);
                    }
                }
                return m; // unresolvable ref — pass through
            }
            // Recurse into all values, skip $defs (not part of the instance schema)
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                String key = (String) entry.getKey();
                if ("$defs".equals(key)) continue; // drop the definitions section
                result.put(key, resolveNode(entry.getValue(), defs));
            }
            return result;
        }
        if (node instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(resolveNode(item, defs));
            }
            return result;
        }
        return node; // primitive — pass through as-is
    }

    private static String pythonDictRepr(Object obj) {
        if (obj == null) return "None";
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("'")
                        .append(entry.getKey())
                        .append("': ")
                        .append(pythonDictRepr(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(pythonDictRepr(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof String) {
            return "'" + obj + "'";
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? "True" : "False";
        }
        if (obj instanceof Number) {
            return obj.toString();
        }
        return "'" + obj + "'";
    }
}
