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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper class for parsing text content that may contain JSON.
 *
 * <p>Attempts to parse text as JSON and returns a JSON object if successful, otherwise returns the
 * original text.
 */
public class JsonTextParser {

    private static final Logger log = LoggerFactory.getLogger(JsonTextParser.class);
    private final ObjectMapper objectMapper;

    public JsonTextParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses text content, attempting to convert JSON strings to JSON objects.
     *
     * <p>If the text is valid JSON, returns the parsed JSON node. Otherwise, returns a text node
     * with the original content.
     *
     * @param text The text to parse
     * @return JsonNode containing either parsed JSON or the original text
     */
    public JsonNode parseTextOrJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return objectMapper.getNodeFactory().textNode(text != null ? text : "");
        }

        String trimmed = text.trim();

        // Check if it looks like JSON (starts with { or [)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return objectMapper.readTree(trimmed);
            } catch (JsonProcessingException e) {
                log.debug(
                        "Text looks like JSON but failed to parse, treating as text: {}",
                        e.getMessage());
                return objectMapper.getNodeFactory().textNode(text);
            }
        }

        // Not JSON, return as text
        return objectMapper.getNodeFactory().textNode(text);
    }

    /**
     * Parses text content and returns it as an Object.
     *
     * <p>If the text is valid JSON, returns the parsed object (Map, List, etc.). Otherwise, returns
     * the original text string.
     *
     * @param text The text to parse
     * @return Object containing either parsed JSON or the original text string
     */
    public Object parseTextOrJsonAsObject(String text) {
        JsonNode node = parseTextOrJson(text);

        if (node.isTextual()) {
            return node.asText();
        }

        // Convert JSON node to appropriate Java object
        return objectMapper.convertValue(node, Object.class);
    }
}
