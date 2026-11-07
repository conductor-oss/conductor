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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates the {@code mcpOptions} input of {@code CALL_MCP_TOOL}.
 *
 * <p>{@code mcpOptions} is part of the persisted workflow contract: a field never changes type or
 * meaning, and new functionality is added as new optional fields. Unknown keys are rejected, so a
 * definition written for a newer Conductor does not silently lose an option on an older one.
 */
public final class McpOptions {

    public static final String PROTOCOL_VERSION = "protocolVersion";

    /** The protocol versions that can be selected. */
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS =
            List.of(McpProtocolHeaders.PROTOCOL_VERSION);

    private static final Set<String> KNOWN_KEYS = Set.of(PROTOCOL_VERSION);

    private McpOptions() {}

    /**
     * Returns the selected protocol version, or {@code null} for a legacy call (no {@code
     * mcpOptions}, or no {@code protocolVersion}).
     *
     * @param mcpOptions the raw {@code mcpOptions} input, after expressions are resolved
     * @throws IllegalArgumentException if {@code mcpOptions} is not an object, has an unknown key,
     *     or has a {@code protocolVersion} that is not a supported string
     */
    public static String protocolVersion(Object mcpOptions) {
        if (mcpOptions == null) {
            return null;
        }
        if (!(mcpOptions instanceof Map<?, ?> options)) {
            throw new IllegalArgumentException(
                    "Invalid mcpOptions: expected a JSON object, got "
                            + typeName(mcpOptions)
                            + ". Example: {\"protocolVersion\": \""
                            + McpProtocolHeaders.PROTOCOL_VERSION
                            + "\"}");
        }

        Set<String> unknown = new TreeSet<>();
        for (Object key : options.keySet()) {
            if (!KNOWN_KEYS.contains(String.valueOf(key))) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid mcpOptions: unknown option(s) "
                            + unknown
                            + "; supported options: "
                            + new TreeSet<>(KNOWN_KEYS));
        }

        Object version = options.get(PROTOCOL_VERSION);
        if (version == null) {
            return null;
        }
        if (!(version instanceof String text)) {
            throw new IllegalArgumentException(
                    "Invalid mcpOptions.protocolVersion: expected a string, got "
                            + typeName(version)
                            + "; supported versions: "
                            + SUPPORTED_PROTOCOL_VERSIONS);
        }
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(text)) {
            throw new IllegalArgumentException(
                    "Unsupported mcpOptions.protocolVersion '"
                            + text
                            + "'; supported versions: "
                            + SUPPORTED_PROTOCOL_VERSIONS
                            + ". Remove protocolVersion to use the legacy MCP request.");
        }
        return text;
    }

    private static String typeName(Object value) {
        if (value instanceof String) {
            return "a string";
        }
        if (value instanceof Number) {
            return "a number";
        }
        if (value instanceof Boolean) {
            return "a boolean";
        }
        if (value instanceof List<?>) {
            return "an array";
        }
        if (value instanceof Map<?, ?>) {
            return "an object";
        }
        return value.getClass().getSimpleName();
    }
}
