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
import org.conductoross.conductor.ai.model.FinishReason;
import org.junit.jupiter.api.Test;
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
    private static final String FIRST_PROVIDER_ID = "provider-a-id";
    private static final String USER_DATA_JSON =
            "{\"timestamp\":123,\"model\":\"user-model\",\"id\":\"user-id\"}";
    private static final String SECOND_PROVIDER_ID = "provider-b-id";
    private static final String REORDERED_USER_DATA_JSON =
            "{\"id\":\"user-id\",\"model\":\"user-model\",\"timestamp\":123}";
    private static final String PROMPT_WITH_PROVIDER_ID =
            "  Do not replace provider-a-id in this text.\n";
    private static final String FIRST_TOOL_REFERENCE = "call_0";
    private static final String USER_ID = "user-id";
    private static final String ID_FIELD = "id";
    private static final String USER_MODEL = "user-model";
    private static final String MODEL_FIELD = "model";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String TOOL_CALL_ID = "a";
    private static final String NESTED_RESULT_JSON =
            "{\"values\":[2,1],\"text\":\"{\\\"id\\\":1}\"}";
    private static final String VALUES_FIELD = "values";
    private static final String TEXT_FIELD = "text";
    private static final String EMBEDDED_JSON_TEXT = "{\"id\":1}";
    private static final String PLAIN_TEXT_RESULT = "  plain text\n";
    private static final String INVALID_JSON_TEXT = "{} trailing";
    private static final String MISSING_TOOL_CALL_ID = "missing";
    private static final String TOOL_NAME = "tool";
    private static final String EMPTY_OBJECT_JSON = "{}";
    private static final String OTHER_TOOL_NAME = "other";
    private static final String END_TURN_REASON = "end_turn";
    private static final String LENGTH_REASON = "length";
    private static final String REFUSAL_REASON = "refusal";
    private static final String UNKNOWN_REASON = "unknown";
    private static final String GREETING = "hello";
    private static final String FIRST_CALL_ID = "first";
    private static final String LISBON_ARGUMENTS_JSON = "{\"city\":\"Lisbon\"}";
    private static final String SECOND_CALL_ID = "second";
    private static final String PARIS_ARGUMENTS_JSON = "{\"city\":\"Paris\"}";
    private static final String SECOND_RESULT_JSON = "{\"value\":2}";
    private static final String FIRST_RESULT_JSON = "{\"value\":1}";
    private static final String SECOND_TOOL_REFERENCE = "call_1";
    private static final String ANSWER = "answer";

    @Test
    void normalizesPhysicalIdsButPreservesUserFieldsAndPromptText() {
        LlmSavedResponses.Request a = normalize(FIRST_PROVIDER_ID, USER_DATA_JSON);
        LlmSavedResponses.Request b = normalize(SECOND_PROVIDER_ID, REORDERED_USER_DATA_JSON);
        assertEquals(a, b);
        assertEquals(PROMPT_WITH_PROVIDER_ID, a.messages().getFirst().text());
        LlmSavedResponses.ToolResult result = a.messages().getLast().toolResults().getFirst();
        assertEquals(FIRST_TOOL_REFERENCE, result.reference());
        assertEquals(USER_ID, result.value().get(ID_FIELD).textValue());
        assertEquals(USER_MODEL, result.value().get(MODEL_FIELD).textValue());
        assertEquals(123, result.value().get(TIMESTAMP_FIELD).intValue());
    }

    @Test
    void preservesArrayOrderAndNestedJsonStrings() {
        JsonNode result =
                normalize(TOOL_CALL_ID, NESTED_RESULT_JSON)
                        .messages()
                        .getLast()
                        .toolResults()
                        .getFirst()
                        .value();
        assertEquals(2, result.get(VALUES_FIELD).get(0).intValue());
        assertTrue(result.get(TEXT_FIELD).isTextual());
        assertEquals(EMBEDDED_JSON_TEXT, result.get(TEXT_FIELD).textValue());
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
                                new Prompt(call(TOOL_CALL_ID, INVALID_JSON_TEXT)),
                                new ChatCompletion()));
    }

    @Test
    void rejectsMissingCallsAndWrongToolNames() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(
                                        result(MISSING_TOOL_CALL_ID, TOOL_NAME, EMPTY_OBJECT_JSON)),
                                new ChatCompletion()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(
                                        List.of(
                                                call(TOOL_CALL_ID, EMPTY_OBJECT_JSON),
                                                result(
                                                        TOOL_CALL_ID,
                                                        OTHER_TOOL_NAME,
                                                        EMPTY_OBJECT_JSON))),
                                new ChatCompletion()));
    }

    @Test
    void normalizesFinishReasonsAndRejectsUnknownReasons() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertEquals(
                FinishReason.STOP,
                converter
                        .toSavedResponse(response(END_TURN_REASON))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertEquals(
                FinishReason.MAX_TOKENS,
                converter
                        .toSavedResponse(response(LENGTH_REASON))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertEquals(
                FinishReason.CONTENT_FILTER,
                converter
                        .toSavedResponse(response(REFUSAL_REASON))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertThrows(
                IllegalArgumentException.class,
                () -> converter.toSavedResponse(response(UNKNOWN_REASON)));
        assertThrows(IllegalArgumentException.class, () -> converter.toSavedResponse(null));
    }

    @Test
    void rejectsProviderNativeTools() {
        ChatCompletion input = new ChatCompletion();
        input.setWebSearch(true);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LlmRequestResponseConverter()
                                .toSavedRequest(new Prompt(GREETING), input));
    }

    @Test
    void repeatedToolNamesKeepDistinctCallResultAssociations() {
        AssistantMessage first = call(FIRST_CALL_ID, LISBON_ARGUMENTS_JSON);
        AssistantMessage second = call(SECOND_CALL_ID, PARIS_ARGUMENTS_JSON);
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        LlmSavedResponses.Request request =
                converter.toSavedRequest(
                        new Prompt(
                                List.of(
                                        first,
                                        second,
                                        result(SECOND_CALL_ID, TOOL_NAME, SECOND_RESULT_JSON),
                                        result(FIRST_CALL_ID, TOOL_NAME, FIRST_RESULT_JSON))),
                        new ChatCompletion());
        assertEquals(
                SECOND_TOOL_REFERENCE,
                request.messages().get(2).toolResults().getFirst().reference());
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
                                new AssistantMessage(ANSWER),
                                ChatGenerationMetadata.builder().finishReason(finish).build())));
    }
}
