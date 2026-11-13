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

import tools.jackson.databind.JsonNode;

/**
 * A JSON-RPC {@code error} object, read leniently: a malformed field is kept as absent or as text,
 * never thrown on, because it comes from the server.
 *
 * @param code the error code, or {@code null} if absent or not an integer in the {@code int} range
 * @param message the error message, or {@code null} if absent; a non-string value is kept as its
 *     JSON text
 * @param data the error {@code data}, or {@code null} if absent
 */
public record McpRpcError(Integer code, String message, JsonNode data) {

    public static McpRpcError from(JsonNode error) {
        JsonNode codeNode = error.get("code");
        // canConvertToInt: an integer outside the int range is kept as no code, not thrown on.
        Integer code =
                codeNode != null && codeNode.isIntegralNumber() && codeNode.canConvertToInt()
                        ? codeNode.asInt()
                        : null;
        JsonNode messageNode = error.get("message");
        // A conforming server sends a string; any other JSON is kept as text, not thrown on.
        String message =
                messageNode == null || messageNode.isNull()
                        ? null
                        : messageNode.isString() ? messageNode.asString() : messageNode.toString();
        return new McpRpcError(code, message, error.get("data"));
    }

    /** {@code JSON-RPC error <code> <message>}, with placeholders for absent fields. */
    public String describe() {
        return "JSON-RPC error "
                + (code != null ? code : "(no code)")
                + " "
                + (message != null ? message : "(no message)");
    }
}
