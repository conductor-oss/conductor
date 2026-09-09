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
        assertEquals(
                "  Do not replace provider-a-id in this text.\n", a.messages().getFirst().text());
        LlmSavedResponses.ToolResult result = a.messages().getLast().toolResults().getFirst();
        assertEquals("call_0", result.reference());
        assertEquals("user-id", result.value().get("id").textValue());
        assertEquals("user-model", result.value().get("model").textValue());
        assertEquals(123, result.value().get("timestamp").intValue());
    }

    @Test
    void preservesArrayOrderAndNestedJsonStrings() {
        JsonNode result =
                normalize("a", "{\"values\":[2,1],\"text\":\"{\\\"id\\\":1}\"}")
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
                "  plain text\n",
                normalize("a", "  plain text\n")
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
                                new Prompt(call("a", "{} trailing")), new ChatCompletion()));
    }

    @Test
    void rejectsMissingCallsAndWrongToolNames() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(result("missing", "tool", "{}")), new ChatCompletion()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        converter.toSavedRequest(
                                new Prompt(List.of(call("a", "{}"), result("a", "other", "{}"))),
                                new ChatCompletion()));
    }

    @Test
    void normalizesFinishReasonsAndRejectsUnknownReasons() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        assertEquals(
                FinishReason.STOP,
                converter
                        .toSavedResponse(response("end_turn"))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertEquals(
                FinishReason.MAX_TOKENS,
                converter
                        .toSavedResponse(response("length"))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertEquals(
                FinishReason.CONTENT_FILTER,
                converter
                        .toSavedResponse(response("refusal"))
                        .completions()
                        .getFirst()
                        .finishReason());
        assertThrows(
                IllegalArgumentException.class,
                () -> converter.toSavedResponse(response("unknown")));
        assertThrows(IllegalArgumentException.class, () -> converter.toSavedResponse(null));
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
        AssistantMessage first = call("first", "{\"city\":\"Lisbon\"}");
        AssistantMessage second = call("second", "{\"city\":\"Paris\"}");
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        LlmSavedResponses.Request request =
                converter.toSavedRequest(
                        new Prompt(
                                List.of(
                                        first,
                                        second,
                                        result("second", "tool", "{\"value\":2}"),
                                        result("first", "tool", "{\"value\":1}"))),
                        new ChatCompletion());
        assertEquals("call_1", request.messages().get(2).toolResults().getFirst().reference());
        assertEquals("call_0", request.messages().get(3).toolResults().getFirst().reference());
    }

    private static LlmSavedResponses.Request normalize(String id, String output) {
        return new LlmRequestResponseConverter()
                .toSavedRequest(
                        new Prompt(
                                List.of(
                                        new UserMessage(
                                                "  Do not replace provider-a-id in this text.\n"),
                                        call(id, "{\"city\":\"Lisbon\"}"),
                                        result(id, "tool", output))),
                        new ChatCompletion());
    }

    private static AssistantMessage call(String id, String args) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", "tool", args)))
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
