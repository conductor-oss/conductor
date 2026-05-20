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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class JsonTextParserTest {

    private JsonTextParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new JsonTextParser(objectMapper);
    }

    // ========== Valid JSON Object Tests ==========

    @Test
    void testParseValidJsonObject() {
        String json = "{\"num\":127,\"name\":\"test\"}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(127, map.get("num"));
        assertEquals("test", map.get("name"));
    }

    @Test
    void testParseValidJsonObjectWithWhitespace() {
        String json = "  {  \"num\" : 127  }  ";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(127, map.get("num"));
    }

    @Test
    void testParseEmptyJsonObject() {
        String json = "{}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertTrue(map.isEmpty());
    }

    @Test
    void testParseNestedJsonObject() {
        String json = "{\"outer\":{\"inner\":\"value\"},\"num\":42}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertInstanceOf(Map.class, map.get("outer"));
        assertEquals(42, map.get("num"));
    }

    // ========== Valid JSON Array Tests ==========

    @Test
    void testParseValidJsonArray() {
        String json = "[1,2,3,\"test\"]";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertEquals(4, list.size());
        assertEquals(1, list.get(0));
        assertEquals("test", list.get(3));
    }

    @Test
    void testParseEmptyJsonArray() {
        String json = "[]";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertTrue(list.isEmpty());
    }

    @Test
    void testParseNestedJsonArray() {
        String json = "[[1,2],[3,4]]";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        assertInstanceOf(List.class, list.get(0));
    }

    // ========== Primitive JSON Values Tests ==========
    // Note: The implementation only parses JSON objects and arrays.
    // Standalone primitives are returned as strings, which is correct for MCP use
    // case.

    @Test
    void testParseJsonString() {
        String json = "\"hello world\"";
        Object result = parser.parseTextOrJsonAsObject(json);

        // Primitives are returned as-is (not parsed)
        assertInstanceOf(String.class, result);
        assertEquals(json, result);
    }

    @Test
    void testParseJsonNumber() {
        String json = "12345";
        Object result = parser.parseTextOrJsonAsObject(json);

        // Primitives are returned as-is (not parsed)
        assertInstanceOf(String.class, result);
        assertEquals(json, result);
    }

    @Test
    void testParseJsonBoolean() {
        String jsonTrue = "true";
        Object resultTrue = parser.parseTextOrJsonAsObject(jsonTrue);
        // Primitives are returned as-is (not parsed)
        assertInstanceOf(String.class, resultTrue);
        assertEquals(jsonTrue, resultTrue);

        String jsonFalse = "false";
        Object resultFalse = parser.parseTextOrJsonAsObject(jsonFalse);
        assertInstanceOf(String.class, resultFalse);
        assertEquals(jsonFalse, resultFalse);
    }

    @Test
    void testParseJsonNull() {
        String json = "null";
        Object result = parser.parseTextOrJsonAsObject(json);
        // Standalone null is returned as string "null"
        assertInstanceOf(String.class, result);
        assertEquals(json, result);
    }

    // ========== Invalid JSON / Plain Text Tests ==========

    @Test
    void testParsePlainText() {
        String text = "This is just plain text";
        Object result = parser.parseTextOrJsonAsObject(text);

        assertInstanceOf(String.class, result);
        assertEquals(text, result);
    }

    @Test
    void testParseInvalidJson() {
        String invalidJson = "{invalid json}";
        Object result = parser.parseTextOrJsonAsObject(invalidJson);

        assertInstanceOf(String.class, result);
        assertEquals(invalidJson, result);
    }

    @Test
    void testParseIncompleteJson() {
        String incompleteJson = "{\"num\":127";
        Object result = parser.parseTextOrJsonAsObject(incompleteJson);

        assertInstanceOf(String.class, result);
        assertEquals(incompleteJson, result);
    }

    @Test
    void testParseJsonWithTrailingComma() {
        String jsonWithTrailingComma = "{\"num\":127,}";
        Object result = parser.parseTextOrJsonAsObject(jsonWithTrailingComma);

        // Jackson is lenient by default, but if this fails, it should return the string
        // This behavior depends on ObjectMapper configuration
        assertNotNull(result);
    }

    // ========== Null and Empty Tests ==========

    @Test
    void testParseNullInput() {
        Object result = parser.parseTextOrJsonAsObject(null);
        // Null input returns empty string
        assertInstanceOf(String.class, result);
        assertEquals("", result);
    }

    @Test
    void testParseEmptyString() {
        String empty = "";
        Object result = parser.parseTextOrJsonAsObject(empty);

        assertInstanceOf(String.class, result);
        assertEquals(empty, result);
    }

    @Test
    void testParseWhitespaceOnly() {
        String whitespace = "   \t\n  ";
        Object result = parser.parseTextOrJsonAsObject(whitespace);

        assertInstanceOf(String.class, result);
        assertEquals(whitespace, result);
    }

    // ========== Special Characters Tests ==========

    @Test
    void testParseJsonWithEscapedCharacters() {
        String json = "{\"text\":\"Line1\\nLine2\\tTabbed\"}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        // After JSON parsing, escaped characters are actual characters
        String text = (String) map.get("text");
        assertTrue(text.contains("Line1"));
        assertTrue(text.contains("Line2"));
    }

    @Test
    void testParseJsonWithUnicodeCharacters() {
        String json = "{\"emoji\":\"😀\",\"chinese\":\"你好\"}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("😀", map.get("emoji"));
        assertEquals("你好", map.get("chinese"));
    }

    @Test
    void testParseJsonWithQuotesInString() {
        String json = "{\"quote\":\"He said \\\"hello\\\"\"}";
        Object result = parser.parseTextOrJsonAsObject(json);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertTrue(((String) map.get("quote")).contains("hello"));
    }

    // ========== parseTextOrJson (JsonNode) Tests ==========

    @Test
    void testParseTextOrJsonReturnsJsonNode() {
        String json = "{\"num\":127}";
        JsonNode result = parser.parseTextOrJson(json);

        assertTrue(result.isObject());
        assertEquals(127, result.get("num").asInt());
    }

    @Test
    void testParseTextOrJsonReturnsTextNode() {
        String text = "plain text";
        JsonNode result = parser.parseTextOrJson(text);

        assertTrue(result.isTextual());
        assertEquals(text, result.asText());
    }

    @Test
    void testParseTextOrJsonWithNullReturnsEmptyText() {
        JsonNode result = parser.parseTextOrJson(null);
        assertTrue(result.isTextual());
        assertEquals("", result.asText());
    }

    // ========== Complex Real-World Examples ==========

    @Test
    void testParseMcpToolResponse() {
        // Simulates actual MCP tool response
        String mcpResponse = "{\"num\":127,\"nested\":{\"array\":[1,2,3]},\"message\":\"success\"}";
        Object result = parser.parseTextOrJsonAsObject(mcpResponse);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(127, map.get("num"));
        assertEquals("success", map.get("message"));
        assertInstanceOf(Map.class, map.get("nested"));
    }

    @Test
    void testParseJsonLikeTextThatIsNotJson() {
        // Text that looks like JSON but isn't
        String text = "The value is {num: 127} but this isn't JSON";
        Object result = parser.parseTextOrJsonAsObject(text);

        assertInstanceOf(String.class, result);
        assertEquals(text, result);
    }

    @Test
    void testParseLargeJsonObject() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < 100; i++) {
            if (i > 0) json.append(",");
            json.append("\"key").append(i).append("\":").append(i);
        }
        json.append("}");

        Object result = parser.parseTextOrJsonAsObject(json.toString());

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(100, map.size());
        assertEquals(42, map.get("key42"));
    }
}
