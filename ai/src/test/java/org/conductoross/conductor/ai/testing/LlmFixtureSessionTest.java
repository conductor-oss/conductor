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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.LLMHelper;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class LlmFixtureSessionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "length,MAX_TOKENS",
        "stop_sequence,STOP",
        "end_turn,STOP",
        "refusal,CONTENT_FILTER"
    })
    void recordingAndReplayExposeTheSameFinishReason(String providerReason, String expected) {
        var recording = LlmFixtureSession.recording("finish_reason");
        var recorded =
                runHelper(
                        recording,
                        input("real", "model"),
                        prompt -> textResponse("answer", providerReason));
        var replay = LlmFixtureSession.replaying(recording.fixture());
        var replayed = runHelper(replay, input("mockLLM", "finish_reason"), null);
        assertEquals(expected, recorded.getFinishReason());
        assertEquals(recorded.getFinishReason(), replayed.getFinishReason());
    }

    @Test
    void wrapperCapturesOnlyFixtureConstraintsBeforeInputMutation() {
        var recording = LlmFixtureSession.recording("constraints");
        var input = input("real", "model");
        var properties = new HashMap<String, Object>(Map.of("type", "string"));
        input.setOutputSchema(
                SchemaDef.builder().type(SchemaDef.Type.JSON).data(properties).build());
        var model = recording.modelFor(input, prompt -> textResponse("ok", "stop"));
        input.setJsonOutput(true);
        properties.put("type", "number");
        input.setWebSearch(true);
        model.call(new Prompt("hello"));
        var request = recording.fixture().entries().getFirst().request();
        assertFalse(request.jsonOutput());
        assertEquals("string", request.outputSchema().path("data").path("type").textValue());
    }

    @Test
    void recordingPreservesProviderDefaultsAndReplayNeverConsultsProvider() {
        var defaults =
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                        .model("provider-default-model")
                        .temperature(0.25)
                        .build();
        ChatModel provider =
                new ChatModel() {
                    public ChatResponse call(Prompt prompt) {
                        throw new AssertionError("Must not invoke provider");
                    }

                    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
                        return defaults;
                    }
                };
        var recording = LlmFixtureSession.recording("weather");
        assertEquals(
                defaults, recording.modelFor(input("real", "model"), provider).getDefaultOptions());
        var replay = LlmFixtureSession.replaying(oneEntry());
        runHelper(replay, input("mockLLM", "weather"), provider);
    }

    @Test
    void recordsRealHelperCallsAndReplaysToolsThroughNormalResponseProcessing() throws Exception {
        var recording = LlmFixtureSession.recording("weather_tool_call");
        var input = input("provider-a", "real-model");
        var first = runHelper(recording, input, prompt -> toolResponse("provider-call-99"));
        assertTrue(first.hasToolCalls());
        assertEquals("get_weather", first.getToolCalls().getFirst().getName());

        var nextInput = input("provider-a", "real-model");
        addHistory(nextInput, first.getToolCalls().getFirst().getTaskReferenceName());
        var next =
                runHelper(
                        recording,
                        nextInput,
                        prompt -> textResponse("Sunny in Lisbon, 21C.", "end_turn"));
        assertEquals("Sunny in Lisbon, 21C.", next.getResult());

        String json = mapper.writeValueAsString(recording.fixture());
        assertFalse(json.contains("provider-call-99"));
        assertFalse(json.contains("real-model"));
        assertFalse(json.contains("provider-a"));
        assertFalse(json.contains("response_id"));
        assertFalse(json.contains("token"));
        var fixture = mapper.readValue(json, LlmFixture.class);
        assertEquals(recording.fixture(), fixture);
        var path = LlmFixtureFiles.write(directory, fixture, false);
        try (var source = java.nio.file.Files.newInputStream(path)) {
            fixture = LlmFixtureFiles.read(source);
        }

        var replay = LlmFixtureSession.replaying(fixture);
        var followupOnly = input("mockLLM", "weather_tool_call");
        addHistory(followupOnly, "unrelated-runtime-id");
        assertEquals(next.getResult(), runHelper(replay, followupOnly, null).getResult());
        var replayFirst = runHelper(replay, input("mockLLM", "weather_tool_call"), null);
        String replayId = replayFirst.getToolCalls().getFirst().getTaskReferenceName();
        assertNotEquals("provider-call-99", replayId);
        assertEquals(
                first.getToolCalls().getFirst().getInputParameters(),
                replayFirst.getToolCalls().getFirst().getInputParameters());
        var replayInput = input("mockLLM", "weather_tool_call");
        addHistory(replayInput, replayId);
        var replayNext = runHelper(replay, replayInput, null);
        assertEquals(next.getResult(), replayNext.getResult());
        assertEquals("STOP", replayNext.getFinishReason());
        assertEquals(0, replayNext.getTokenUsed());
    }

    @Test
    void unmatchedRequestFailsWithoutAffectingLaterMatches() {
        var replay = LlmFixtureSession.replaying(oneEntry());
        var wrong = input("mockLLM", "weather");
        wrong.getMessages().getFirst().setMessage("secret changed prompt");
        var error = assertThrows(NonRetryableException.class, () -> runHelper(replay, wrong, null));
        assertTrue(error.getMessage().contains("No matching request"));
        assertFalse(error.getMessage().contains("secret changed prompt"));
        assertEquals("ok", runHelper(replay, input("mockLLM", "weather"), null).getResult());
        assertEquals("ok", runHelper(replay, input("mockLLM", "weather"), null).getResult());
    }

    @Test
    void toolSchemaAndToolResultChangesFailReplay() {
        var recording = LlmFixtureSession.recording("weather");
        var result =
                runHelper(recording, input("real", "model"), prompt -> toolResponse("call-id"));
        var followup = input("real", "model");
        addHistory(followup, result.getToolCalls().getFirst().getTaskReferenceName());
        runHelper(recording, followup, prompt -> textResponse("done", "stop"));

        var replay = LlmFixtureSession.replaying(recording.fixture());
        var wrongSchema = input("mockLLM", "weather");
        wrongSchema.getTools().getFirst().setDescription("different description");
        assertThrows(NonRetryableException.class, () -> runHelper(replay, wrongSchema, null));
        var first = runHelper(replay, input("mockLLM", "weather"), null);
        var wrongResult = input("mockLLM", "weather");
        addHistory(wrongResult, first.getToolCalls().getFirst().getTaskReferenceName());
        wrongResult
                .getMessages()
                .getLast()
                .getToolCalls()
                .getFirst()
                .setOutput(Map.of("temp_c", -10));
        assertThrows(NonRetryableException.class, () -> runHelper(replay, wrongResult, null));
    }

    @Test
    void replayMatchesRequestsInAnyOrderAndCanRepeatThem() {
        var recording = LlmFixtureSession.recording("weather");
        var first = input("real", "model");
        var second = input("real", "model");
        second.getMessages().getFirst().setMessage("Weather in Paris?");
        runHelper(recording, first, prompt -> textResponse("Lisbon", "stop"));
        runHelper(recording, second, prompt -> textResponse("Paris", "stop"));
        var fixture = recording.fixture();
        var replay = LlmFixtureSession.replaying(fixture);
        for (int i = 0; i < 2; i++) {
            var replayInput = input("mockLLM", "weather");
            replayInput.getMessages().getFirst().setMessage("Weather in Paris?");
            assertEquals("Paris", runHelper(replay, replayInput, null).getResult());
        }
        assertEquals("Lisbon", runHelper(replay, input("mockLLM", "weather"), null).getResult());
        assertEquals(fixture, replay.fixture());
    }

    @Test
    void conflictingResponsesForTheSameRequestAreRejected() {
        var recording = LlmFixtureSession.recording("weather");
        runHelper(recording, input("real", "model"), prompt -> textResponse("one", "stop"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        runHelper(
                                recording,
                                input("real", "model"),
                                prompt -> textResponse("two", "stop")));
        var entry = recording.fixture().entries().getFirst();
        var otherResponse =
                new LlmFixtureNormalizer().normalizeResponse(textResponse("two", "stop"));
        var ambiguous =
                new LlmFixture(
                        1,
                        "weather",
                        List.of(entry, new LlmFixture.Entry(entry.request(), otherResponse)));
        assertThrows(IllegalArgumentException.class, () -> LlmFixtureSession.replaying(ambiguous));
    }

    @Test
    void oneReplaySupportsConcurrentRepeatedRequests() throws Exception {
        var fixture = oneEntry();
        var replay = LlmFixtureSession.replaying(fixture);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Callable<Object>>();
            for (int i = 0; i < 20; i++) {
                jobs.add(() -> runHelper(replay, input("mockLLM", "weather"), null).getResult());
            }
            for (var future : pool.invokeAll(jobs)) assertEquals("ok", future.get());
        }
        assertEquals(fixture, replay.fixture());
    }

    @Test
    void recordingDoesNotSerializeOrDeduplicateProviderCalls() throws Exception {
        var recording = LlmFixtureSession.recording("parallel");
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var calls = new AtomicInteger();
        ChatModel provider =
                prompt -> {
                    calls.incrementAndGet();
                    try {
                        barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("Provider calls blocked each other", e);
                    }
                    return toolResponse(java.util.UUID.randomUUID().toString());
                };
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Object> call = () -> runHelper(recording, input("real", "model"), provider);
            for (var future : pool.invokeAll(List.of(call, call))) assertNotNull(future.get());
        }
        assertEquals(2, calls.get());
        assertEquals(1, recording.fixture().entries().size());
        var replay = LlmFixtureSession.replaying(recording.fixture());
        var first = runHelper(replay, input("mockLLM", "parallel"), null);
        var second = runHelper(replay, input("mockLLM", "parallel"), null);
        assertNotEquals(
                first.getToolCalls().getFirst().getTaskReferenceName(),
                second.getToolCalls().getFirst().getTaskReferenceName());
    }

    @Test
    void recordingPreservesInvalidJsonSoReplayExercisesHelperValidation() {
        var recording = LlmFixtureSession.recording("invalid_json");
        var input = input("real", "model");
        input.setJsonOutput(true);
        assertThrows(
                RuntimeException.class,
                () -> runHelper(recording, input, prompt -> textResponse("not json", "stop")));
        assertEquals(
                "not json",
                recording
                        .fixture()
                        .entries()
                        .getFirst()
                        .response()
                        .completions()
                        .getFirst()
                        .message()
                        .text());
        var replay = LlmFixtureSession.replaying(recording.fixture());
        var replayInput = input("mockLLM", "invalid_json");
        replayInput.setJsonOutput(true);
        assertThrows(RuntimeException.class, () -> runHelper(replay, replayInput, null));
    }

    @Test
    void failedProviderCallDoesNotPublishAnEntry() {
        var recording = LlmFixtureSession.recording("failure");
        assertThrows(
                IllegalStateException.class,
                () ->
                        runHelper(
                                recording,
                                input("real", "model"),
                                prompt -> {
                                    throw new IllegalStateException("provider failed");
                                }));
        assertTrue(recording.fixture().entries().isEmpty());
    }

    @Test
    void rejectsMockRecordingAndProviderSideChaining() {
        var recording = LlmFixtureSession.recording("weather");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        recording.modelFor(
                                input("mockLLM", "weather"), p -> textResponse("ok", "stop")));
        var input = input("real", "model");
        input.setPreviousResponseId("provider-response");
        var calls = new AtomicInteger();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        runHelper(
                                recording,
                                input,
                                p -> {
                                    calls.incrementAndGet();
                                    return textResponse("ok", "stop");
                                }));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnknownVersionAndUnsafeScenarioNames() {
        assertThrows(IllegalArgumentException.class, () -> new LlmFixture(2, "weather", List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> LlmFixtureSession.recording("../weather"));
    }

    private LlmFixture oneEntry() {
        var recording = LlmFixtureSession.recording("weather");
        runHelper(recording, input("real", "model"), p -> textResponse("ok", "stop"));
        return recording.fixture();
    }

    private static ChatCompletion input(String provider, String model) {
        var input = new ChatCompletion();
        input.setLlmProvider(provider);
        input.setModel(model);
        input.setInstructions("Answer weather questions.");
        input.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Weather in Lisbon?"));
        var tool = new ToolSpec();
        tool.setName("get_weather");
        tool.setDescription("Get weather");
        tool.setInputSchema(
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        input.getTools().add(tool);
        return input;
    }

    private static void addHistory(ChatCompletion input, String id) {
        var call =
                org.conductoross.conductor.ai.model.ToolCall.builder()
                        .taskReferenceName(id)
                        .name("get_weather")
                        .inputParameters(Map.of("city", "Lisbon"))
                        .output(Map.of("temp_c", 21))
                        .build();
        input.getMessages().add(new ChatMessage(ChatMessage.Role.tool_call, call));
        input.getMessages().add(new ChatMessage(ChatMessage.Role.tool, call));
    }

    private static org.conductoross.conductor.ai.model.LLMResponse runHelper(
            LlmFixtureSession session, ChatCompletion input, ChatModel delegate) {
        ChatModel wrapped = session.modelFor(input, delegate);
        AIModel provider =
                new AIModel() {
                    public String getModelProvider() {
                        return input.getLlmProvider();
                    }

                    public ChatModel getChatModel() {
                        return wrapped;
                    }

                    public ImageModel getImageModel() {
                        throw new UnsupportedOperationException();
                    }

                    public List<Float> generateEmbeddings(EmbeddingGenRequest request) {
                        throw new UnsupportedOperationException();
                    }
                };
        var task = new Task();
        task.setTaskId(java.util.UUID.randomUUID().toString());
        task.setWorkflowInstanceId("workflow");
        return new LLMHelper(null, List.of())
                .chatComplete(task, provider, input, null, usage -> {});
    }

    private static ChatResponse toolResponse(String id) {
        return new ChatResponse(
                List.of(
                        new Generation(
                                AssistantMessage.builder()
                                        .content("")
                                        .toolCalls(
                                                List.of(
                                                        new AssistantMessage.ToolCall(
                                                                id,
                                                                "function",
                                                                "get_weather",
                                                                "{\"city\":\"Lisbon\"}")))
                                        .build(),
                                ChatGenerationMetadata.builder()
                                        .finishReason("tool_use")
                                        .build())));
    }

    private static ChatResponse textResponse(String text, String finish) {
        return new ChatResponse(
                List.of(
                        new Generation(
                                new AssistantMessage(text),
                                ChatGenerationMetadata.builder().finishReason(finish).build())),
                ChatResponseMetadata.builder()
                        .id("provider-response-id")
                        .model("provider-model")
                        .usage(new DefaultUsage(12, 13, 25))
                        .build());
    }
}
