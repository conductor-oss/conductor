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
package org.conductoross.conductor.ai.mcp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * Mirrors tool parameters annotated with {@code x-mcp-header} into {@code Mcp-Param-*} headers (MCP
 * {@code 2026-07-28}, <i>Streamable HTTP › Custom Headers from Tool Parameters</i>). Used only for
 * calls that select {@code mcpOptions.protocolVersion}.
 */
public final class McpHeaderParams {

    public static final String ANNOTATION = "x-mcp-header";
    public static final String HEADER_PREFIX = "Mcp-Param-";

    private static final Set<String> PRIMITIVE_TYPES = Set.of("string", "integer", "boolean");

    /**
     * Schema keywords whose values are instance data, not subschemas. An {@code x-mcp-header} key
     * inside them is part of an example or a constant, not an annotation, so they are not walked.
     */
    private static final Set<String> DATA_KEYWORDS =
            Set.of("default", "examples", "example", "const", "enum");

    private static final BigInteger MAX_SAFE_INTEGER = BigInteger.valueOf(9007199254740991L);
    private static final BigInteger MIN_SAFE_INTEGER = MAX_SAFE_INTEGER.negate();

    private McpHeaderParams() {}

    /**
     * Validates the tool's {@code x-mcp-header} annotations and returns the headers to send for the
     * given arguments, in schema order.
     *
     * @param toolName the tool name, used in error messages
     * @param inputSchema the tool's {@code inputSchema}; may be {@code null} or missing
     * @param arguments the call's tool arguments
     * @throws McpCallException if an annotation is invalid, which makes the tool unusable, or if an
     *     annotated argument has a value that cannot be sent as a header
     */
    public static Map<String, String> headersFor(
            String toolName, JsonNode inputSchema, Map<String, Object> arguments) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Binding binding : bindings(toolName, inputSchema)) {
            Object value = valueAt(arguments, binding.path());
            if (value == null) {
                continue;
            }
            headers.put(
                    HEADER_PREFIX + binding.name(),
                    McpProtocolHeaders.encodeHeaderValue(toText(toolName, binding, value)));
        }
        return headers;
    }

    /** One valid annotation: the header name suffix and the property path from the root. */
    record Binding(String name, List<String> path) {}

    static List<Binding> bindings(String toolName, JsonNode inputSchema) {
        List<Binding> bindings = new ArrayList<>();
        if (inputSchema == null || !inputSchema.isObject()) {
            return bindings;
        }
        walkSchema(toolName, inputSchema, new ArrayList<>(), true, bindings);

        Set<String> seen = new HashSet<>();
        for (Binding binding : bindings) {
            if (!seen.add(binding.name().toLowerCase(Locale.ROOT))) {
                throw invalid(
                        toolName,
                        binding,
                        "header name '" + binding.name() + "' is used more than once");
            }
        }
        return bindings;
    }

    /**
     * Visits a schema object. {@code onPropertyPath} is true only when the object was reached from
     * the root through {@code properties} keys alone, which is the only place an annotation is
     * valid.
     */
    private static void walkSchema(
            String toolName,
            JsonNode schema,
            List<String> path,
            boolean onPropertyPath,
            List<Binding> bindings) {
        for (Map.Entry<String, JsonNode> field : schema.properties()) {
            String key = field.getKey();
            JsonNode child = field.getValue();
            if (ANNOTATION.equals(key)) {
                bindings.add(checkAnnotation(toolName, schema, path, onPropertyPath, child));
            } else if ("properties".equals(key) && child.isObject()) {
                for (Map.Entry<String, JsonNode> property : child.properties()) {
                    if (property.getValue().isObject()) {
                        List<String> childPath = new ArrayList<>(path);
                        childPath.add(property.getKey());
                        walkSchema(
                                toolName, property.getValue(), childPath, onPropertyPath, bindings);
                    }
                }
            } else if (!DATA_KEYWORDS.contains(key)) {
                walkOther(toolName, child, path, bindings);
            }
        }
    }

    /**
     * Visits schema keywords other than {@code properties}; annotations found there are invalid.
     */
    private static void walkOther(
            String toolName, JsonNode node, List<String> path, List<Binding> bindings) {
        if (node.isObject()) {
            walkSchema(toolName, node, path, false, bindings);
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                walkOther(toolName, element, path, bindings);
            }
        }
    }

    private static Binding checkAnnotation(
            String toolName,
            JsonNode schema,
            List<String> path,
            boolean onPropertyPath,
            JsonNode annotation) {
        String name = annotation.isString() ? annotation.asString() : null;
        Binding binding = new Binding(name == null ? String.valueOf(annotation) : name, path);
        if (!onPropertyPath || path.isEmpty()) {
            throw invalid(
                    toolName,
                    binding,
                    "it must be on a property reached only through 'properties' keys");
        }
        if (name == null || !isToken(name)) {
            throw invalid(toolName, binding, "the header name must be a non-empty HTTP token");
        }
        JsonNode type = schema.get("type");
        if (type == null || !type.isString() || !PRIMITIVE_TYPES.contains(type.asString())) {
            throw invalid(
                    toolName,
                    binding,
                    "the property type must be 'string', 'integer' or 'boolean', but is "
                            + (type == null ? "missing" : type.toString()));
        }
        return binding;
    }

    private static Object valueAt(Map<String, Object> arguments, List<String> path) {
        Object current = arguments;
        for (String segment : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static String toText(String toolName, Binding binding, Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        if (value instanceof Number number) {
            BigInteger integer = toSafeInteger(number);
            if (integer != null) {
                return integer.toString();
            }
        }
        throw new McpCallException(
                "MCP tool '"
                        + toolName
                        + "': the value of argument '"
                        + String.join(".", binding.path())
                        + "' (x-mcp-header '"
                        + binding.name()
                        + "') must be a string, a boolean, or an integer between -(2^53-1) and"
                        + " 2^53-1");
    }

    private static BigInteger toSafeInteger(Number number) {
        BigInteger integer;
        try {
            integer = new BigDecimal(number.toString()).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
        return integer.compareTo(MIN_SAFE_INTEGER) >= 0 && integer.compareTo(MAX_SAFE_INTEGER) <= 0
                ? integer
                : null;
    }

    private static boolean isToken(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean alphaNumeric =
                    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!alphaNumeric && "!#$%&'*+-.^_`|~".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static McpCallException invalid(String toolName, Binding binding, String reason) {
        String where =
                binding.path().isEmpty() ? "the schema root" : String.join(".", binding.path());
        return new McpCallException(
                "MCP tool '"
                        + toolName
                        + "' has an invalid x-mcp-header annotation '"
                        + binding.name()
                        + "' at "
                        + where
                        + ": "
                        + reason);
    }
}
