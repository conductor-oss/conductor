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
package org.conductoross.conductor.ai.recording;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.providers.anthropic.Anthropic;
import org.conductoross.conductor.ai.providers.anthropic.AnthropicConfiguration;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.conductoross.conductor.ai.providers.openai.OpenAI;
import org.conductoross.conductor.ai.providers.openai.OpenAIConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.*;

class RecordedRequestNormalizerTest {
    private static final String PROMPT_WITH_PROVIDER_ID =
            "  Do not replace provider-a-id in this text.\n";
    private static final String FIRST_TOOL_REFERENCE = "call_0";
    private static final String TOOL_CALL_ID = "a";
    private static final String PLAIN_TEXT_RESULT = "  plain text\n";
    private static final String TOOL_NAME = "tool";
    private static final String FIRST_CALL_ID = "first";
    private static final String LISBON_ARGUMENTS_JSON = "{\"city\":\"Lisbon\"}";
    private static final String SECOND_CALL_ID = "second";

    @TempDir Path directory;

    @Test
    void normalizesPhysicalIdsButPreservesUserFieldsAndPromptText() {
        LLMRecording.Request a =
                normalize(
                        "provider-a-id",
                        "{\"timestamp\":123,\"model\":\"user-model\",\"id\":\"user-id\"}");
        LLMRecording.Request b =
                normalize(
                        "provider-b-id",
                        "{\"id\":\"user-id\",\"model\":\"user-model\",\"timestamp\":123}");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(PROMPT_WITH_PROVIDER_ID, a.messages().getFirst().text());
        LLMRecording.ToolResult result = a.messages().getLast().toolResults().getFirst();
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
        RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        normalizer.normalize(
                                new Prompt(call(TOOL_CALL_ID, "{} trailing")),
                                new ChatCompletion()));
    }

    @Test
    void rejectsMissingCallsAndWrongToolNames() {
        RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        normalizer.normalize(
                                new Prompt(result("missing", TOOL_NAME, "{}")),
                                new ChatCompletion()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        normalizer.normalize(
                                new Prompt(
                                        List.of(
                                                call(TOOL_CALL_ID, "{}"),
                                                result(TOOL_CALL_ID, "other", "{}"))),
                                new ChatCompletion()));
    }

    @Test
    void rejectsProviderNativeTools() {
        ChatCompletion input = new ChatCompletion();
        input.setWebSearch(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RecordedRequestNormalizer().normalize(new Prompt("hello"), input));
    }

    @Test
    void repeatedToolNamesKeepDistinctCallResultAssociations() {
        AssistantMessage first = call(FIRST_CALL_ID, LISBON_ARGUMENTS_JSON);
        AssistantMessage second = call(SECOND_CALL_ID, "{\"city\":\"Paris\"}");
        RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
        LLMRecording.Request request =
                normalizer.normalize(
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

    @Test
    void generationOptionsAffectRequestMatching() {
        ChatCompletion input = generationInput();

        LLMRecording.Request recorded = normalize(input);
        assertDifferentRequest(recorded, value -> value.setTemperature(0.4));
        assertDifferentRequest(recorded, value -> value.setTopP(0.6));
        assertDifferentRequest(recorded, value -> value.setTopK(8));
        assertDifferentRequest(recorded, value -> value.setFrequencyPenalty(0.2));
        assertDifferentRequest(recorded, value -> value.setPresencePenalty(0.4));
        assertDifferentRequest(recorded, value -> value.setStopWords(List.of("end")));
        assertDifferentRequest(recorded, value -> value.setMaxTokens(100));
        assertDifferentRequest(recorded, value -> value.setThinkingTokenLimit(200));
        assertDifferentRequest(recorded, value -> value.setReasoningEffort("medium"));
        assertDifferentRequest(recorded, value -> value.setReasoningSummary("concise"));
    }

    private static ChatCompletion generationInput() {
        ChatCompletion input = new ChatCompletion();
        input.setTemperature(0.2);
        input.setTopP(0.8);
        input.setTopK(12);
        input.setFrequencyPenalty(0.1);
        input.setPresencePenalty(0.3);
        input.setStopWords(List.of("stop"));
        input.setMaxTokens(200);
        input.setThinkingTokenLimit(100);
        input.setReasoningEffort("high");
        input.setReasoningSummary("detailed");
        return input;
    }

    @Test
    void providerAndModelDoNotAffectRequestMatching() {
        ChatCompletion first = new ChatCompletion();
        first.setLlmProvider("first");
        first.setModel("first-model");
        ChatCompletion second = new ChatCompletion();
        second.setLlmProvider("second");
        second.setModel("second-model");
        assertEquals(normalize(first), normalize(second));
    }

    @Test
    void providerOptionTransformationsDoNotChangePlaybackMatching() throws IOException {
        ChatCompletion input = generationInput();
        MockLLM mock = new MockLLM(directory, new ObjectMapper());

        input.setModel("claude");
        input.setMaxTokens(null);
        ChatOptions anthropicOptions =
                new Anthropic(new AnthropicConfiguration(), new OkHttpClient())
                        .getChatOptions(input);
        assertEquals(1.0, anthropicOptions.getTemperature());
        assertEquals(8192, anthropicOptions.getMaxTokens());
        assertMatchesMock(mock, input, anthropicOptions);

        input.setModel("gpt-5");
        ChatOptions openAiOptions =
                new OpenAI(new OpenAIConfiguration(), new OkHttpClient()).getChatOptions(input);
        assertNull(openAiOptions.getTemperature());
        assertNull(openAiOptions.getTopP());
        assertNull(openAiOptions.getStopSequences());
        assertMatchesMock(mock, input, openAiOptions);
    }

    private static LLMRecording.Request normalize(String id, String output) {
        return new RecordedRequestNormalizer()
                .normalize(
                        new Prompt(
                                List.of(
                                        new UserMessage(PROMPT_WITH_PROVIDER_ID),
                                        call(id, LISBON_ARGUMENTS_JSON),
                                        result(id, TOOL_NAME, output))),
                        new ChatCompletion());
    }

    private static LLMRecording.Request normalize(ChatCompletion input) {
        return new RecordedRequestNormalizer().normalize(new Prompt("hello"), input);
    }

    private static void assertDifferentRequest(
            LLMRecording.Request recorded, Consumer<ChatCompletion> change) {
        ChatCompletion changed = generationInput();
        change.accept(changed);
        assertNotEquals(recorded, normalize(changed));
    }

    private static void assertMatchesMock(
            MockLLM mock, ChatCompletion input, ChatOptions providerOptions) {
        LLMRecording.Request recorded = normalize(input, providerOptions);
        assertEquals(recorded, normalize(input, mock.getChatOptions(input)));
    }

    private static LLMRecording.Request normalize(ChatCompletion input, ChatOptions options) {
        return new RecordedRequestNormalizer()
                .normalize(new Prompt("hello", options), RecordedRequestNormalizer.options(input));
    }

    private static AssistantMessage call(String id, String args) {
        return AssistantMessage.builder()
                .content(StringUtils.EMPTY)
                .toolCalls(
                        List.of(
                                new AssistantMessage.ToolCall(
                                        id,
                                        RecordedRequestNormalizer.FUNCTION_TOOL_TYPE,
                                        TOOL_NAME,
                                        args)))
                .build();
    }

    private static ToolResponseMessage result(String id, String name, String output) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, output)))
                .build();
    }
}
