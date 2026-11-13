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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec: "Opted-in tool calls mirror x-mcp-header parameters" (task 2.5). */
class McpHeaderParamsTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    @Test
    void annotatedParameterIsMirrored() {
        JsonNode schema = schema("{\"region\":{\"type\":\"string\",\"x-mcp-header\":\"Region\"}}");

        assertEquals(
                Map.of("Mcp-Param-Region", "us-west1"),
                McpHeaderParams.headersFor("execute_sql", schema, Map.of("region", "us-west1")));
    }

    @Test
    void annotatedParameterIsAbsentOrNull() {
        JsonNode schema = schema("{\"region\":{\"type\":\"string\",\"x-mcp-header\":\"Region\"}}");
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("region", null);

        assertEquals(Map.of(), McpHeaderParams.headersFor("t", schema, Map.of()));
        assertEquals(Map.of(), McpHeaderParams.headersFor("t", schema, nullValue));
    }

    @Test
    void integerAndBooleanValues() {
        JsonNode schema =
                schema(
                        "{\"n\":{\"type\":\"integer\",\"x-mcp-header\":\"N\"},"
                                + "\"b\":{\"type\":\"boolean\",\"x-mcp-header\":\"B\"}}");

        assertEquals(
                Map.of("Mcp-Param-N", "42", "Mcp-Param-B", "true"),
                McpHeaderParams.headersFor("t", schema, Map.of("n", 42, "b", true)));
        assertEquals(
                Map.of("Mcp-Param-N", "-9007199254740991", "Mcp-Param-B", "false"),
                McpHeaderParams.headersFor(
                        "t", schema, Map.of("n", -9007199254740991L, "b", false)));
    }

    @Test
    void nonAsciiParameterValue() {
        JsonNode schema =
                schema("{\"greeting\":{\"type\":\"string\",\"x-mcp-header\":\"Greeting\"}}");

        assertEquals(
                Map.of("Mcp-Param-Greeting", "=?base64?SGVsbG8sIOS4lueVjA==?="),
                McpHeaderParams.headersFor("t", schema, Map.of("greeting", "Hello, 世界")));
    }

    @Test
    void nestedPropertyPathIsMirrored() {
        JsonNode schema =
                schema(
                        "{\"target\":{\"type\":\"object\",\"properties\":{"
                                + "\"region\":{\"type\":\"string\",\"x-mcp-header\":\"Region\"}}}}");

        assertEquals(
                Map.of("Mcp-Param-Region", "eu"),
                McpHeaderParams.headersFor("t", schema, Map.of("target", Map.of("region", "eu"))));
        assertEquals(
                Map.of(),
                McpHeaderParams.headersFor("t", schema, Map.of("target", "not-an-object")));
    }

    @Test
    void schemaWithoutAnnotationsOrWithoutSchemaGivesNoHeaders() {
        assertEquals(
                Map.of(),
                McpHeaderParams.headersFor(
                        "t", schema("{\"q\":{\"type\":\"string\"}}"), Map.of("q", "x")));
        assertEquals(Map.of(), McpHeaderParams.headersFor("t", null, Map.of("q", "x")));
    }

    @Test
    void invalidAnnotationOnNumberTypedProperty() {
        assertInvalid(
                "{\"count\":{\"type\":\"number\",\"x-mcp-header\":\"Count\"}}",
                "MCP tool 'execute_sql' has an invalid x-mcp-header annotation 'Count'",
                "'string', 'integer' or 'boolean'");
    }

    @Test
    void invalidAnnotationWithMissingType() {
        assertInvalid("{\"count\":{\"x-mcp-header\":\"Count\"}}", "'Count'", "missing");
    }

    @Test
    void invalidAnnotationWithNonTokenOrEmptyName() {
        assertInvalid(
                "{\"r\":{\"type\":\"string\",\"x-mcp-header\":\"Bad Name\"}}",
                "'Bad Name'",
                "HTTP token");
        assertInvalid("{\"r\":{\"type\":\"string\",\"x-mcp-header\":\"\"}}", "''", "HTTP token");
        assertInvalid("{\"r\":{\"type\":\"string\",\"x-mcp-header\":7}}", "'7'", "HTTP token");
    }

    @Test
    void invalidAnnotationWithDuplicateNameIgnoringCase() {
        assertInvalid(
                "{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"Region\"},"
                        + "\"b\":{\"type\":\"string\",\"x-mcp-header\":\"region\"}}",
                "'region'",
                "more than once");
    }

    @Test
    void invalidAnnotationOutsideAPropertiesPath() {
        assertInvalid(
                "{\"list\":{\"type\":\"array\",\"items\":"
                        + "{\"type\":\"string\",\"x-mcp-header\":\"Item\"}}}",
                "'Item'",
                "'properties' keys");

        JsonNode rootAnnotated = MAPPER.readTree("{\"type\":\"string\",\"x-mcp-header\":\"Root\"}");
        McpCallException e =
                assertThrows(
                        McpCallException.class,
                        () -> McpHeaderParams.headersFor("t", rootAnnotated, Map.of()));
        assertTrue(e.getMessage().contains("the schema root"), e.getMessage());
    }

    @Test
    void propertyNamedLikeTheAnnotationIsNotAnAnnotation() {
        JsonNode schema = schema("{\"x-mcp-header\":{\"type\":\"string\"}}");

        assertEquals(
                Map.of(), McpHeaderParams.headersFor("t", schema, Map.of("x-mcp-header", "value")));
    }

    @Test
    void runtimeValueThatIsNotAPrimitiveFailsBeforeSending() {
        JsonNode schema = schema("{\"n\":{\"type\":\"integer\",\"x-mcp-header\":\"N\"}}");

        for (Object bad : new Object[] {1.5, 9007199254740992L, Map.of("a", 1)}) {
            McpCallException e =
                    assertThrows(
                            McpCallException.class,
                            () -> McpHeaderParams.headersFor("t", schema, Map.of("n", bad)));
            assertTrue(e.getMessage().contains("argument 'n'"), e.getMessage());
            assertTrue(e.getMessage().contains("x-mcp-header 'N'"), e.getMessage());
        }
    }

    private static void assertInvalid(String properties, String... expectedInMessage) {
        JsonNode schema = schema(properties);
        McpCallException e =
                assertThrows(
                        McpCallException.class,
                        () -> McpHeaderParams.headersFor("execute_sql", schema, Map.of()));
        for (String expected : expectedInMessage) {
            assertTrue(e.getMessage().contains(expected), e.getMessage());
        }
    }

    @Test
    void annotationKeysInsideDataKeywordsAreIgnored() {
        JsonNode schema =
                schema(
                        "{\"config\":{\"type\":\"object\","
                                + "\"default\":{\"x-mcp-header\":\"Default\"},"
                                + "\"examples\":[{\"x-mcp-header\":\"Example\"}],"
                                + "\"example\":{\"x-mcp-header\":\"Example2\"},"
                                + "\"const\":{\"x-mcp-header\":\"Const\"},"
                                + "\"enum\":[{\"x-mcp-header\":\"Enum\"}]}}");

        assertEquals(
                Map.of(),
                McpHeaderParams.headersFor(
                        "t", schema, Map.of("config", Map.of("x-mcp-header", "v"))));
    }

    private static JsonNode schema(String properties) {
        return MAPPER.readTree("{\"type\":\"object\",\"properties\":" + properties + "}");
    }
}
