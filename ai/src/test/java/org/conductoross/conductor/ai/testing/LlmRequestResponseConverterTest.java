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
package org.conductoross.conductor.ai.testing;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class LlmRequestResponseConverterTest {
    private static final String PROMPT_WITH_PROVIDER_ID =
            "  Do not replace provider-a-id in this text.\n";
    private static final String FIRST_TOOL_REFERENCE = "call_0";
    private static final String TOOL_CALL_ID = "a";
    private static final String PLAIN_TEXT_RESULT = "  plain text\n";
    private static final String TOOL_NAME = "tool";
    private static final String FIRST_CALL_ID = "first";
    private static final String LISBON_ARGUMENTS_JSON = "{\"city\":\"Lisbon\"}";
    private static final String SECOND_CALL_ID = "second";

    @Test
    void normalizesPhysicalIdsButPreservesUserFieldsAndPromptText() {
        LlmSavedResponses.Request a =
                normalize(
                        "provider-a-id",
                        "{\"timestamp\":123,\"model\":\"user-model\",\"id\":\"user-id\"}");
        LlmSavedResponses.Request b =
                normalize(
                        "provider-b-id",
                        "{\"id\":\"user-id\",\"model\":\"user-model\",\"timestamp\":123}");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(PROMPT_WITH_PROVIDER_ID, a.messages().getFirst().text());
        LlmSavedResponses.ToolResult result = a.messages().getLast().toolResults().getFirst();
        assertEquals(FIRST_TOOL_REFERENCE, result.reference());
        assertEquals("user-id", result.value().get("id").textValue());
        assertEquals("user-model", result.value().get("model").textValue());
        assertEquals(123, result.value().get("timestamp").intValue());
    }

    @Test
    void preservesArrayOrderAndNestedJsonStrings() {
        JsonNode result =
                normalize(TOOL_CALL_ID, "{\"values\":[2,1],\"text\":\"{\\\"id\\\":1}\"}")
                        .messages()
                        .getLast()
                        .toolResults()
                        .getFirst()
                        .value();
        assertEquals(2, result.get("values").get(0).intValue());
        assertTrue(result.get("text").isTextual());
        assertEquals("{\"id\":1}", result.get("text").textValue());
    }

    @Test
    void preservesNonJsonToolResultsAndRejectsTruncatedArgumentParsing() {
        assertEquals(
                PLAIN_TEXT_RESULT,
                normalize(TOOL_CALL_ID, PLAIN_TEXT_RESULT)
                        .messages()
                        .getLast()
                        .toolResults()
                        .getFirst()
                        .value()
                        .textValue());
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(call(TOOL_CALL_ID, "{} trailing")),
                                new ChatCompletion()));
    }

    @Test
    void rejectsMissingCallsAndWrongToolNames() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(result("missing", TOOL_NAME, "{}")),
                                new ChatCompletion()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(
                                        List.of(
                                                call(TOOL_CALL_ID, "{}"),
                                                result(TOOL_CALL_ID, "other", "{}"))),
                                new ChatCompletion()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"end_turn", "length", "refusal", "COMPLETE", "STOP_SEQUENCE", "unknown"})
    void preservesProviderFinishReasons(String finishReason) {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        JsonNode saved = converter.toSavedResponse(response(finishReason));
        assertEquals(finishReason, saved.at("/results/0/metadata/finishReason").asText());
        assertEquals(
                finishReason,
                converter
                        .toChatResponse(saved, "replay")
                        .getResult()
                        .getMetadata()
                        .getFinishReason());
    }

    @Test
    void rejectsAbsentModelResponses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LlmRequestResponseConverter().toSavedResponse(null));
    }

    @Test
    void rejectsProviderNativeTools() {
        ChatCompletion input = new ChatCompletion();
        input.setWebSearch(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LlmRequestResponseConverter().toSavedRequest(new Prompt("hello"), input));
    }

    @Test
    void repeatedToolNamesKeepDistinctCallResultAssociations() {
        AssistantMessage first = call(FIRST_CALL_ID, LISBON_ARGUMENTS_JSON);
        AssistantMessage second = call(SECOND_CALL_ID, "{\"city\":\"Paris\"}");
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        LlmSavedResponses.Request request =
                converter.toSavedRequest(
                        new Prompt(
                                List.of(
                                        first,
                                        second,
                                        result(SECOND_CALL_ID, TOOL_NAME, "{\"value\":2}"),
                                        result(FIRST_CALL_ID, TOOL_NAME, "{\"value\":1}"))),
                        new ChatCompletion());
        assertEquals("call_1", request.messages().get(2).toolResults().getFirst().reference());
        assertEquals(
                FIRST_TOOL_REFERENCE,
                request.messages().get(3).toolResults().getFirst().reference());
    }

    private static LlmSavedResponses.Request normalize(String id, String output) {
        return new LlmRequestResponseConverter()
                .toSavedRequest(
                        new Prompt(
                                List.of(
                                        new UserMessage(PROMPT_WITH_PROVIDER_ID),
                                        call(id, LISBON_ARGUMENTS_JSON),
                                        result(id, TOOL_NAME, output))),
                        new ChatCompletion());
    }

    private static AssistantMessage call(String id, String args) {
        return AssistantMessage.builder()
                .content(StringUtils.EMPTY)
                .toolCalls(
                        List.of(
                                new AssistantMessage.ToolCall(
                                        id,
                                        LlmRequestResponseConverter.FUNCTION_TOOL_TYPE,
                                        TOOL_NAME,
                                        args)))
                .build();
    }

    private static ToolResponseMessage result(String id, String name, String output) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, output)))
                .build();
    }

    private static ChatResponse response(String finish) {
        return new ChatResponse(
                List.of(
                        new Generation(
                                new AssistantMessage("answer"),
                                ChatGenerationMetadata.builder().finishReason(finish).build())));
    }
}
