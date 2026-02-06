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
package org.conductoross.conductor.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LLMHelperTest {

    private LLMHelper llm;

    @BeforeEach
    void setUp() {
        // Create a test instance - we'll use reflection to test private methods
        llm = new LLMHelper(null, null);
    }

    @Test
    void testParseNestedJsonStringsWithSimpleNestedJson() throws Exception {
        // Test the parseNestedJsonStrings method using reflection
        Map<String, Object> input = new HashMap<>();
        input.put("query", "");
        input.put("filter", "{\"value\":\"page\"}");
        input.put("simple", "value");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = llm.parseNestedJsonStrings(input);

        // Verify that the JSON string was parsed into an object
        assertNotNull(result);
        assertEquals("", result.get("query"));
        assertEquals("value", result.get("simple"));

        // The filter should be parsed from string to Map
        Object filter = result.get("filter");
        assertTrue(filter instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> filterMap = (Map<String, Object>) filter;
        assertEquals("page", filterMap.get("value"));
    }

    @Test
    void testParseNestedJsonStringsWithDeeplyNestedJson() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(
                "user",
                "{\"profile\":{\"preferences\":{\"theme\":\"dark\",\"notifications\":{\"email\":true}}}}");
        input.put("simple", "value");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = llm.parseNestedJsonStrings(input);

        assertNotNull(result);
        assertEquals("value", result.get("simple"));

        // The user should be parsed into a nested structure
        Object user = result.get("user");
        assertTrue(user instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) user;

        Object profile = userMap.get("profile");
        assertTrue(profile instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> profileMap = (Map<String, Object>) profile;

        Object preferences = profileMap.get("preferences");
        assertTrue(preferences instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> preferencesMap = (Map<String, Object>) preferences;

        assertEquals("dark", preferencesMap.get("theme"));

        Object notifications = preferencesMap.get("notifications");
        assertTrue(notifications instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> notificationsMap = (Map<String, Object>) notifications;

        assertEquals(true, notificationsMap.get("email"));
    }

    @Test
    void testParseNestedJsonStringsWithArrayJson() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("items", "[{\"id\":1,\"name\":\"test\"},{\"id\":2,\"name\":\"test2\"}]");
        input.put("simple", "value");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = llm.parseNestedJsonStrings(input);

        assertNotNull(result);
        assertEquals("value", result.get("simple"));

        // The items should be parsed into a List
        Object items = result.get("items");
        assertTrue(items instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> itemsList = (List<Object>) items;

        assertEquals(2, itemsList.size());

        // First item should be a Map
        Object firstItem = itemsList.get(0);
        assertTrue(firstItem instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstItemMap = (Map<String, Object>) firstItem;
        assertEquals(1, firstItemMap.get("id"));
        assertEquals("test", firstItemMap.get("name"));
    }

    @Test
    void testParseNestedJsonStringsWithMixedDataTypes() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("string", "simple string");
        input.put("number", 42);
        input.put("boolean", true);
        input.put("jsonString", "{\"nested\":\"value\"}");
        input.put("arrayString", "[1,2,3]");
        input.put("nestedMap", Map.of("key", "value"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = llm.parseNestedJsonStrings(input);

        assertNotNull(result);
        assertEquals("simple string", result.get("string"));
        assertEquals(42, result.get("number"));
        assertEquals(true, result.get("boolean"));

        // JSON strings should be parsed
        Object jsonString = result.get("jsonString");
        assertTrue(jsonString instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonStringMap = (Map<String, Object>) jsonString;
        assertEquals("value", jsonStringMap.get("nested"));

        // Array strings should be parsed
        Object arrayString = result.get("arrayString");
        assertTrue(arrayString instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> arrayList = (List<Object>) arrayString;
        assertEquals(3, arrayList.size());
        assertEquals(1, arrayList.get(0));
        assertEquals(2, arrayList.get(1));
        assertEquals(3, arrayList.get(2));

        // Nested maps should be preserved
        Object nestedMap = result.get("nestedMap");
        assertTrue(nestedMap instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedMapMap = (Map<String, Object>) nestedMap;
        assertEquals("value", nestedMapMap.get("key"));
    }

    @Test
    void testParseNestedJsonStringsWithInvalidJson() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("validJson", "{\"key\":\"value\"}");
        input.put("invalidJson", "not a json string");
        input.put("emptyString", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = llm.parseNestedJsonStrings(input);

        assertNotNull(result);

        // Valid JSON should be parsed
        Object validJson = result.get("validJson");
        assertTrue(validJson instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> validJsonMap = (Map<String, Object>) validJson;
        assertEquals("value", validJsonMap.get("key"));

        // Invalid JSON should remain as string
        assertEquals("not a json string", result.get("invalidJson"));
        assertEquals("", result.get("emptyString"));
    }

    @Test
    void testIsJsonString() throws Exception {

        // Test valid JSON strings
        assertTrue(llm.isJsonString("{\"key\":\"value\"}"));
        assertTrue(llm.isJsonString("[1,2,3]"));
        assertTrue(llm.isJsonString("{\"nested\":{\"key\":\"value\"}}"));

        // Test invalid JSON strings
        assertFalse(llm.isJsonString("not json"));
        assertFalse(llm.isJsonString(""));
        assertFalse(llm.isJsonString(null));
        assertFalse(llm.isJsonString("123"));
        assertFalse(llm.isJsonString("true"));
    }

    @Test
    void testExtractMethodFromInputParametersWithDynamicKey() {
        // Test the exact structure from the example
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("method", "jira-getIssue");
        nestedMap.put("integrationName", "Jira");
        nestedMap.put("issueIdOrKey", "CDX-436");
        inputParameters.put("toolu_01YDpSHJ9NQKE452s7Mhvy5L", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertEquals("jira-getIssue", method);
    }

    @Test
    void testExtractMethodFromInputParametersWithNullInput() {
        String method = llm.extractMethodFromInputParameters(null);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithEmptyMap() {
        Map<String, Object> inputParameters = new HashMap<>();
        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithoutMethodKey() {
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("integrationName", "Jira");
        nestedMap.put("issueIdOrKey", "CDX-436");
        inputParameters.put("toolu_01YDpSHJ9NQKE452s7Mhvy5L", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithNonMapValues() {
        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put("key1", "string value");
        inputParameters.put("key2", 123);
        inputParameters.put("key3", List.of("item1", "item2"));

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMultipleEntries() {
        // Test with multiple entries, method is in the second one
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> firstMap = new HashMap<>();
        firstMap.put("otherKey", "otherValue");
        inputParameters.put("firstKey", firstMap);

        Map<String, Object> secondMap = new HashMap<>();
        secondMap.put("method", "slack-sendMessage");
        secondMap.put("channel", "#general");
        inputParameters.put("secondKey", secondMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertEquals("slack-sendMessage", method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMethodAsString() {
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("method", "github-createIssue");
        inputParameters.put("dynamicKey", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertEquals("github-createIssue", method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMethodAsInteger() {
        // Method value should be converted to string
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("method", 12345);
        inputParameters.put("dynamicKey", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertEquals("12345", method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMethodAsNull() {
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("method", null);
        inputParameters.put("dynamicKey", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMixedStructure() {
        // Mix of map and non-map values, method is in one of the maps
        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put("stringKey", "string value");
        inputParameters.put("numberKey", 42);

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("method", "custom-action");
        nestedMap.put("param1", "value1");
        inputParameters.put("toolKey", nestedMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertEquals("custom-action", method);
    }

    @Test
    void testExtractMethodFromInputParametersWithDeepNesting() {
        // Test that it only looks at the first level of nesting
        Map<String, Object> inputParameters = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        Map<String, Object> deepNestedMap = new HashMap<>();
        deepNestedMap.put("method", "deep-method");
        nestedMap.put("deep", deepNestedMap);
        inputParameters.put("outerKey", nestedMap);

        // Should not find method because it's nested too deep
        String method = llm.extractMethodFromInputParameters(inputParameters);
        assertNull(method);
    }

    @Test
    void testExtractMethodFromInputParametersWithMultipleMapsFirstHasMethod() {
        // Test that it returns the first method found
        Map<String, Object> inputParameters = new HashMap<>();

        Map<String, Object> firstMap = new HashMap<>();
        firstMap.put("method", "first-method");
        inputParameters.put("firstKey", firstMap);

        Map<String, Object> secondMap = new HashMap<>();
        secondMap.put("method", "second-method");
        inputParameters.put("secondKey", secondMap);

        String method = llm.extractMethodFromInputParameters(inputParameters);
        // Should return the first method found (order may vary, but should return one of them)
        assertNotNull(method);
        assertTrue(method.equals("first-method") || method.equals("second-method"));
    }
}
