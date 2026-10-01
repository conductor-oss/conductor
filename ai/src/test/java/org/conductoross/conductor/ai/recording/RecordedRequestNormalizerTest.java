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
import java.nio.file.Files;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.*;

class RecordedRequestNormalizerTest {
    private static final RecordedRequestNormalizer.RequestOptions DEFAULT_OPTIONS =
            RecordedRequestNormalizer.options(new ChatCompletion());
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
                                new Prompt(call(TOOL_CALL_ID, "{} trailing")), DEFAULT_OPTIONS));
    }

    @Test
    void rejectsMissingCallsAndWrongToolNames() {
        RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        normalizer.normalize(
                                new Prompt(result("missing", TOOL_NAME, "{}")), DEFAULT_OPTIONS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        normalizer.normalize(
                                new Prompt(
                                        List.of(
                                                call(TOOL_CALL_ID, "{}"),
                                                result(TOOL_CALL_ID, "other", "{}"))),
                                DEFAULT_OPTIONS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RecordedRequestNormalizer()
                                .normalize(
                                        new Prompt(result("missing", "CALL_MCP_TOOL", "{}")),
                                        DEFAULT_OPTIONS));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CALL_MCP_TOOL",
                "GET",
                "HEAD",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS",
                "TRACE",
                "CONNECT"
            })
    void transportResultsMatchTheirOriginalCallsById(String resultName) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RecordedRequestNormalizer()
                                .normalize(
                                        new Prompt(result("missing", resultName, "{}")),
                                        DEFAULT_OPTIONS));
        AssistantMessage calls =
                AssistantMessage.builder()
                        .content(StringUtils.EMPTY)
                        .toolCalls(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                "reverse-id",
                                                "function",
                                                "string_reverse",
                                                "{\"text\":\"hello world\"}"),
                                        new AssistantMessage.ToolCall(
                                                "add-id",
                                                "function",
                                                "math_add",
                                                "{\"a\":33,\"b\":21}")))
                        .build();
        LLMRecording.Request transport =
                new RecordedRequestNormalizer()
                        .normalize(
                                new Prompt(
                                        List.of(
                                                calls,
                                                result("add-id", resultName, "{\"result\":54}"),
                                                result(
                                                        "reverse-id",
                                                        resultName,
                                                        "{\"result\":\"dlrow olleh\"}"))),
                                DEFAULT_OPTIONS);
        LLMRecording.Request named =
                new RecordedRequestNormalizer()
                        .normalize(
                                new Prompt(
                                        List.of(
                                                calls,
                                                result("add-id", "math_add", "{\"result\":54}"),
                                                result(
                                                        "reverse-id",
                                                        "string_reverse",
                                                        "{\"result\":\"dlrow olleh\"}"))),
                                DEFAULT_OPTIONS);
        assertEquals(named, transport);
        LLMRecording.ToolResult addition = transport.messages().get(1).toolResults().getFirst();
        assertEquals("call_1", addition.reference());
        assertEquals("math_add", addition.name());
        assertEquals(54, addition.value().get("result").intValue());
        LLMRecording.ToolResult reversed = transport.messages().get(2).toolResults().getFirst();
        assertEquals("call_0", reversed.reference());
        assertEquals("string_reverse", reversed.name());
        assertEquals("dlrow olleh", reversed.value().get("result").textValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"content\":[{\"type\":\"text\",\"text\":\"hello\",\"parsed\":{\"result\":54.0}}],\"isError\":false}",
                "{\"report\":\"hello\"}"
            })
    void legacyToolSummaryPlaysBackWithFreshIdsAndPreservesTheFixture(String output)
            throws Exception {
        LLMRecording.Request legacy =
                historyRequest(
                        List.of("string_reverse"),
                        List.of(output),
                        "[{\"name\":\"call_oldId_\",\"output\":"
                                + output.replace("54.0", "54")
                                + "}]",
                        false);
        MockLLM playback = playback(legacy);
        byte[] before = Files.readAllBytes(directory.resolve("1_legacy.json"));
        String id = "4568d36c-c784-4e82-a8ba-e2c3a99ad77b_0";
        Prompt prompt =
                new Prompt(
                        List.of(
                                new UserMessage(
                                        summary(
                                                "[{\"name\":\""
                                                        + id
                                                        + "_\",\"output\":"
                                                        + output.replace("54.0", "54")
                                                        + "}]")),
                                AssistantMessage.builder()
                                        .content("{}")
                                        .toolCalls(
                                                List.of(
                                                        new AssistantMessage.ToolCall(
                                                                id,
                                                                "function",
                                                                "string_reverse",
                                                                "{}")))
                                        .build(),
                                result(id, "CALL_MCP_TOOL", output)));
        assertEquals(
                "saved answer",
                playback.getChatModel().call(prompt).getResult().getOutput().getText());
        LLMRecording.Request normalized =
                new RecordedRequestNormalizer().normalize(prompt, DEFAULT_OPTIONS);
        assertEquals(RecordedRequestNormalizer.normalizeTransportHistory(legacy), normalized);
        assertEquals(normalized, RecordedRequestNormalizer.normalizeTransportHistory(normalized));
        var changedMessages = new java.util.ArrayList<>(prompt.getInstructions());
        changedMessages.set(
                0,
                new UserMessage(
                        prompt.getInstructions().getFirst().getText().replace("hello", "changed")));
        assertThrows(
                NonRetryableException.class,
                () -> playback.getChatModel().call(new Prompt(changedMessages)));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("1_legacy.json")));
    }

    @Test
    void ordersGeneratedParallelResultsByCallOrderButPreservesSequentialTurns() throws Exception {
        List<String> names = List.of("check_inventory", "process_order");
        List<String> outputs = List.of("{\"quantity\":12}", "{\"status\":\"cancelled\"}");
        String forward =
                "[{\"name\":\"check_inventory\",\"output\":{\"quantity\":12}},"
                        + "{\"name\":\"process_order\",\"output\":{\"status\":\"cancelled\"}}]";
        String reverse =
                "[{\"name\":\"process_order\",\"output\":{\"status\":\"cancelled\"}},"
                        + "{\"name\":\"check_inventory\",\"output\":{\"quantity\":12}}]";
        LLMRecording.Request first = historyRequest(names, outputs, forward, false);
        LLMRecording.Request second = historyRequest(names, outputs, reverse, false);
        assertEquals(
                RecordedRequestNormalizer.normalizeTransportHistory(first),
                RecordedRequestNormalizer.normalizeTransportHistory(second));
        assertEquals(
                "saved answer",
                playback(first)
                        .getChatModel()
                        .call(prompt(second))
                        .getResult()
                        .getOutput()
                        .getText());
        assertNotEquals(
                RecordedRequestNormalizer.normalizeTransportHistory(
                        historyRequest(names, outputs, forward, true)),
                RecordedRequestNormalizer.normalizeTransportHistory(
                        historyRequest(names, outputs, reverse, true)));
    }

    @Test
    void httpPlaybackIgnoresOnlyListedTransportHeaders() throws Exception {
        String original = httpOutput("old-date", "old-request", 20);
        String fresh = httpOutput("new-date", "new-request", 19);
        LLMRecording.Request saved = httpHistory(original);
        MockLLM playback = playback(saved);
        assertEquals(
                "saved answer",
                playback.getChatModel()
                        .call(prompt(httpHistory(fresh)))
                        .getResult()
                        .getOutput()
                        .getText());
        assertEquals(
                saved, httpHistory(original), "Normalization must not mutate saved JSON values");
        ObjectMapper mapper = new ObjectMapper();
        for (String changed :
                List.of(
                        fresh.replace("200", "201"),
                        fresh.replace("user-date", "changed-body-date"),
                        fresh.replace("application/json", "text/plain"),
                        fresh.replace("\"Retry-After\":[\"10\"]", "\"Retry-After\":[\"20\"]"))) {
            assertNotEquals(mapper.readTree(fresh), mapper.readTree(changed));
            assertThrows(
                    NonRetryableException.class,
                    () -> playback.getChatModel().call(prompt(httpHistory(changed))));
        }
        LLMRecording.Request normalized =
                RecordedRequestNormalizer.normalizeTransportHistory(saved);
        assertEquals(normalized, RecordedRequestNormalizer.normalizeTransportHistory(normalized));
    }

    @Test
    void preservesUnverifiedSummariesAndOrdinaryHeaderPayloads() throws Exception {
        String output = "{\"headers\":{\"Date\":\"user-data\"},\"items\":[2,1]}";
        for (String entries :
                List.of(
                        "not-json",
                        "[]",
                        "[{\"name\":\"tool\",\"output\":{\"changed\":true}}]",
                        "[{\"name\":\"unknown\",\"output\":" + output + "}]",
                        "[{\"name\":\"tool\",\"output\":" + output + ",\"extra\":true}]")) {
            LLMRecording.Request request =
                    historyRequest(List.of("tool"), List.of(output), entries, false);
            assertEquals(request, RecordedRequestNormalizer.normalizeTransportHistory(request));
        }
        LLMRecording.Request request =
                historyRequest(
                        List.of("tool"),
                        List.of(output),
                        "[{\"name\":\"tool\",\"output\":" + output + "}]",
                        false);
        assertEquals(
                new ObjectMapper().readTree(output),
                RecordedRequestNormalizer.normalizeTransportHistory(request)
                        .messages()
                        .getLast()
                        .toolResults()
                        .getFirst()
                        .value());
    }

    @Test
    void normalizationStillRejectsConflictingSavedAnswers() throws Exception {
        LLMRecording.Request first = httpHistory(httpOutput("first-date", "first-request", 10));
        playback(first);
        LLMRecording.Request second = httpHistory(httpOutput("second-date", "second-request", 9));
        new ObjectMapper()
                .writeValue(
                        directory.resolve("2_conflict.json").toFile(),
                        savedRecording(second, "different answer"));
        assertThrows(
                IllegalArgumentException.class, () -> new MockLLM(directory, new ObjectMapper()));
    }

    private MockLLM playback(LLMRecording.Request request) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(
                directory.resolve("1_legacy.json").toFile(),
                savedRecording(request, "saved answer"));
        return new MockLLM(directory, mapper);
    }

    private static LLMRecording savedRecording(LLMRecording.Request request, String answer) {
        return new LLMRecording(
                LLMRecording.SCHEMA_VERSION,
                request,
                RecordedResponseJson.write(
                        new ChatResponse(List.of(new Generation(new AssistantMessage(answer))))));
    }

    private static String summary(String entries) {
        return "[TOOL RESULTS]\n" + entries + "\n[/TOOL RESULTS]\n\nContinue the task.";
    }

    private static String httpOutput(String date, String request, int remaining) {
        return "{\"response\":{\"statusCode\":200,\"reasonPhrase\":\"OK\","
                + "\"body\":{\"Date\":\"user-date\",\"items\":[2,1]},\"headers\":{"
                + "\"dAtE\":[\""
                + date
                + "\"],\"X-GitHub-Request-Id\":[\""
                + request
                + "\"],\"x-github-edge-region\":[\""
                + request
                + "\"],\"X-RateLimit-Remaining\":[\""
                + remaining
                + "\"],\"Content-Type\":[\"application/json\"],\"Retry-After\":[\"10\"]}}}";
    }

    private static LLMRecording.Request httpHistory(String output) throws Exception {
        return historyRequest(
                List.of("list_repos"),
                List.of(output),
                "[{\"name\":\"list_repos\",\"output\":" + output + "}]",
                false);
    }

    private static LLMRecording.Request historyRequest(
            List<String> names, List<String> outputs, String summaryEntries, boolean sequential)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var messages = new java.util.ArrayList<LLMRecording.Message>();
        messages.add(
                new LLMRecording.Message("user", summary(summaryEntries), List.of(), List.of()));
        var calls = new java.util.ArrayList<LLMRecording.ToolCall>();
        for (int i = 0; i < names.size(); i++) {
            calls.add(
                    new LLMRecording.ToolCall(
                            "call_" + i, names.get(i), mapper.createObjectNode()));
        }
        if (!sequential)
            messages.add(new LLMRecording.Message("assistant", "{}", calls, List.of()));
        for (int i = 0; i < names.size(); i++) {
            if (sequential)
                messages.add(
                        new LLMRecording.Message(
                                "assistant", "{}", List.of(calls.get(i)), List.of()));
            messages.add(
                    new LLMRecording.Message(
                            "tool",
                            "",
                            List.of(),
                            List.of(
                                    new LLMRecording.ToolResult(
                                            "call_" + i,
                                            names.get(i),
                                            mapper.readTree(outputs.get(i))))));
        }
        return new LLMRecording.Request(
                messages,
                List.of(),
                false,
                mapper.nullNode(),
                RecordedRequestNormalizer.options(new ChatCompletion()).generationOptions());
    }

    private static Prompt prompt(LLMRecording.Request request) {
        var messages = new java.util.ArrayList<org.springframework.ai.chat.messages.Message>();
        for (LLMRecording.Message message : request.messages()) {
            switch (message.role()) {
                case "user" -> messages.add(new UserMessage(message.text()));
                case "assistant" ->
                        messages.add(
                                AssistantMessage.builder()
                                        .content(message.text())
                                        .toolCalls(
                                                message.toolCalls().stream()
                                                        .map(
                                                                call ->
                                                                        new AssistantMessage
                                                                                .ToolCall(
                                                                                call.reference(),
                                                                                "function",
                                                                                call.name(),
                                                                                call.arguments()
                                                                                        .toString()))
                                                        .toList())
                                        .build());
                case "tool" ->
                        messages.add(
                                ToolResponseMessage.builder()
                                        .responses(
                                                message.toolResults().stream()
                                                        .map(
                                                                result ->
                                                                        new ToolResponseMessage
                                                                                .ToolResponse(
                                                                                result.reference(),
                                                                                result.name(),
                                                                                result.value()
                                                                                        .toString()))
                                                        .toList())
                                        .build());
                default -> throw new IllegalArgumentException(message.role());
            }
        }
        return new Prompt(messages);
    }

    @Test
    void rejectsProviderNativeTools() {
        ChatCompletion input = new ChatCompletion();
        input.setWebSearch(true);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RecordedRequestNormalizer()
                                .normalize(
                                        new Prompt("hello"),
                                        RecordedRequestNormalizer.options(input)));
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
                        DEFAULT_OPTIONS);
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
                        DEFAULT_OPTIONS);
    }

    private static LLMRecording.Request normalize(ChatCompletion input) {
        return new RecordedRequestNormalizer()
                .normalize(new Prompt("hello"), RecordedRequestNormalizer.options(input));
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
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", TOOL_NAME, args)))
                .build();
    }

    private static ToolResponseMessage result(String id, String name, String output) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, output)))
                .build();
    }
}
