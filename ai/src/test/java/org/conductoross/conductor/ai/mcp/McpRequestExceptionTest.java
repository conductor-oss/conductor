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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Error classification for opted-in responses (task 2.7, design § 5 "Error classification"). */
class McpRequestExceptionTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    @ParameterizedTest(name = "HTTP {0}, code {1} -> recognized={2}")
    @CsvSource({
        "400, -32020, true",
        "400, -32021, true",
        "400, -32022, true",
        "200, -32022, true",
        "404, -32601, true",
        "400, -32601, false",
        "400, -32602, true",
        "404, -32602, false",
        "400, -32600, false",
        "500, -32603, false"
    })
    void classifiesJsonRpcErrorBodies(int status, int code, boolean recognized) {
        McpRequestException e =
                McpRequestException.fromHttpError(
                        "tools/call",
                        status,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":"
                                + code
                                + ",\"message\":\"m\"}}",
                        MAPPER);

        assertEquals(code, e.getCode());
        assertEquals(status, e.getHttpStatus());
        assertEquals(recognized, e.isRecognizedProtocolError());
    }

    @Test
    void keepsMessageAndDataOfARecognizedError() {
        McpRequestException e =
                McpRequestException.fromHttpError(
                        "tools/call",
                        400,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32022,"
                                + "\"message\":\"Unsupported protocol version\","
                                + "\"data\":{\"supported\":[\"2025-11-25\"]}}}",
                        MAPPER);

        assertEquals("Unsupported protocol version", e.getRpcMessage());
        assertEquals("2025-11-25", e.getData().get("supported").get(0).asString());
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
        assertTrue(e.getMessage().contains("-32022"), e.getMessage());
        assertTrue(e.getMessage().contains("2025-11-25"), e.getMessage());
        assertFalse(e.isOlderServerRejection());
    }

    @ParameterizedTest(name = "HTTP {0} with body [{1}]")
    @CsvSource(
            value = {"400|''", "404|'Not Found'", "405|<html>no</html>", "400|{\"foo\":1}"},
            delimiter = '|')
    void unrecognizedFourHundredOnToolsCallIsTheOlderServerOutcome(int status, String body) {
        McpRequestException e =
                McpRequestException.fromHttpError("tools/call", status, body, MAPPER);

        assertNull(e.getCode());
        assertFalse(e.isRecognizedProtocolError());
        assertTrue(e.isOlderServerRejection());
        assertTrue(e.getMessage().contains("HTTP " + status), e.getMessage());
    }

    @ParameterizedTest(name = "HTTP {0} with body [{1}]")
    @CsvSource(
            value = {"400|''", "404|'Not Found'", "405|<html>no</html>"},
            delimiter = '|')
    void unrecognizedFourHundredOnTheSchemaLookupIsTheOlderServerOutcome(int status, String body) {
        assertTrue(
                McpRequestException.fromHttpError("tools/list", status, body, MAPPER)
                        .isOlderServerRejection());
    }

    @ParameterizedTest(name = "HTTP {0} on {1}")
    @CsvSource({"406,tools/call", "415,tools/call", "406,tools/list", "415,tools/list"})
    void otherRequestFormStatusesAreTheOlderServerOutcome(int status, String method) {
        assertTrue(
                McpRequestException.fromHttpError(method, status, "", MAPPER)
                        .isOlderServerRejection());
    }

    @ParameterizedTest(name = "HTTP {0} on {1}")
    @CsvSource({
        "401,tools/call",
        "403,tools/call",
        "407,tools/call",
        "408,tools/call",
        "429,tools/call",
        "401,tools/list",
        "403,tools/list",
        "429,tools/list"
    })
    void credentialAndLimitStatusesAreNotTheOlderServerOutcome(int status, String method) {
        assertFalse(
                McpRequestException.fromHttpError(method, status, "", MAPPER)
                        .isOlderServerRejection());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{\"detail\":\"busy\"}|{\"detail\":\"busy\"}",
                "[\"busy\"]|[\"busy\"]",
                "null|",
                "\"\"|''"
            })
    void nonStringMessageIsKeptAsTextAndTheErrorIsStillClassified(String message, String expected) {
        McpRequestException e =
                McpRequestException.fromHttpError(
                        "tasks/get",
                        503,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,"
                                + "\"message\":"
                                + message
                                + "}}",
                        MAPPER);

        assertEquals(503, e.getHttpStatus());
        assertEquals(Integer.valueOf(-32000), e.getCode());
        assertEquals(expected, e.getRpcMessage());
    }

    @Test
    void codeOutsideTheIntRangeIsKeptAsNoCodeAndTheErrorIsStillClassified() {
        McpRequestException e =
                McpRequestException.fromHttpError(
                        "tasks/get",
                        503,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":99999999999,"
                                + "\"message\":\"busy\"}}",
                        MAPPER);

        assertEquals(503, e.getHttpStatus());
        assertNull(e.getCode());
        assertEquals("busy", e.getRpcMessage());
    }

    @Test
    void olderServerOutcomeExcludesTaskRequestsAndOtherStatuses() {
        assertFalse(
                McpRequestException.fromHttpError("tasks/get", 400, "", MAPPER)
                        .isOlderServerRejection());
        assertFalse(
                McpRequestException.fromHttpError("tasks/cancel", 404, "", MAPPER)
                        .isOlderServerRejection());
        assertFalse(
                McpRequestException.fromHttpError("tools/call", 500, "", MAPPER)
                        .isOlderServerRejection());
        assertFalse(
                McpRequestException.fromHttpError("tools/list", 500, "", MAPPER)
                        .isOlderServerRejection());
    }

    @Test
    void longBodiesAreTruncatedInTheMessage() {
        McpRequestException e =
                McpRequestException.fromHttpError("tools/call", 502, "x".repeat(2000), MAPPER);

        assertTrue(e.getMessage().length() < 600, e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "-32603, true, true",
        "-32700, false, true",
        "-32600, false, true",
        "-32601, false, true",
        "-32602, false, true",
        "-32002, false, true",
        "-32020, false, true",
        "-32021, false, true",
        "-32022, false, true",
        "-32042, false, true",
        "-32000, false, false",
        "-32001, false, false",
        "-32050, false, false",
        "-32099, false, false",
        "-32100, false, false",
        "-31999, false, false",
        "-1, false, false",
        "1001, false, false"
    })
    void internalErrorAndCodesWithADefinedMeaning(
            int code, boolean internal, boolean definedMeaning) {
        McpRequestException e =
                McpRequestException.fromHttpError(
                        "tasks/get",
                        200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":"
                                + code
                                + ",\"message\":\"x\"}}",
                        MAPPER);

        assertEquals(internal, e.isInternalError());
        assertEquals(definedMeaning, e.hasDefinedMeaning());
    }

    @Test
    void noJsonRpcErrorHasNoDefinedMeaning() {
        McpRequestException e = McpRequestException.fromHttpError("tasks/get", 503, "busy", MAPPER);

        assertFalse(e.isInternalError());
        assertFalse(e.hasDefinedMeaning());
    }
}
