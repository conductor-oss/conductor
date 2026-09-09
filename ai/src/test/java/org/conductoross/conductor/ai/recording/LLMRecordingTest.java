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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.ModelConfiguration;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.ai.model.ToolCall;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.conductoross.conductor.ai.providers.anthropic.Anthropic;
import org.conductoross.conductor.ai.providers.anthropic.AnthropicConfiguration;
import org.conductoross.conductor.ai.providers.gemini.GeminiVertex;
import org.conductoross.conductor.ai.providers.gemini.GeminiVertexConfiguration;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.conductoross.conductor.ai.providers.mock.MockLLMConfiguration;
import org.conductoross.conductor.ai.providers.openai.OpenAI;
import org.conductoross.conductor.ai.providers.openai.OpenAIConfiguration;
import org.conductoross.conductor.ai.tasks.worker.LLMWorkers;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.dao.schema.InMemorySchemaDAO;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.conductoross.conductor.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.*;

class LLMRecordingTest {

    private static final String REAL_PROVIDER = "real";
    private static final String WEATHER_RESPONSE = "Sunny";
    private static final String PROVIDER_TOOL_CALL_ID = "provider-call-id";
    private static final String INVALID_RECORDING_FILE = "broken.json";
    private static final String INVALID_RECORDING_CONTENT = "not JSON";
    private static final String WEATHER_TOOL_NAME = "get_weather";

    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void startupFlagsControlRecorderAndProvider(boolean record, boolean playback) {
        try (AnnotationConfigApplicationContext context = context(record, playback, null)) {
            assertEquals(record ? 1 : 0, context.getBeansOfType(LLMCallRecorder.class).size());
            assertEquals(
                    playback ? 1 : 0, context.getBeansOfType(MockLLMConfiguration.class).size());
            AIModelProvider providers = context.getBean(AIModelProvider.class);
            if (playback)
                assertInstanceOf(MockLLM.class, providers.getModel(input(MockLLM.NAME, "model")));
            else
                assertThrows(
                        RuntimeException.class,
                        () -> providers.getModel(input(MockLLM.NAME, "model")));
        }
    }

    @Test
    void disabledRecordingLeavesRealCallsAlone() throws IOException {
        try (AnnotationConfigApplicationContext context =
                context(false, false, prompt -> textResponse("ok", "stop"))) {
            assertEquals("ok", call(context, input(REAL_PROVIDER, "model")).getResult());
        }
        assertTrue(recordings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLETE,COMPLETE",
        "STOP_SEQUENCE,STOP_SEQUENCE",
        "length,LENGTH",
        "end_turn,STOP",
        "tool_use,TOOL_CALLS",
        "refusal,CONTENT_FILTER",
        "custom,CUSTOM"
    })
    void liveRecordingAndPlaybackKeepExistingFinishReasons(String providerReason, String expected)
            throws IOException {
        ChatModel provider = prompt -> textResponse("ok", providerReason);
        try (AnnotationConfigApplicationContext context = context(false, false, provider)) {
            assertEquals(expected, call(context, input(REAL_PROVIDER, "model")).getFinishReason());
        }
        try (AnnotationConfigApplicationContext context = context(true, false, provider)) {
            assertEquals(expected, call(context, input(REAL_PROVIDER, "model")).getFinishReason());
        }
        LLMRecording saved =
                new ObjectMapper().readValue(recordings().getFirst().toFile(), LLMRecording.class);
        assertEquals(
                providerReason,
                saved.entries()
                        .getFirst()
                        .response()
                        .at("/results/0/metadata/finishReason")
                        .asText());
        try (AnnotationConfigApplicationContext context = context(false, true, null)) {
            assertEquals(expected, call(context, input(MockLLM.NAME, "model")).getFinishReason());
        }
    }

    @ParameterizedTest
    @MethodSource("providersWithCustomOptions")
    void providerToolDefinitionsSurviveRecordingAndPlayback(AIModel provider, boolean withSchema)
            throws IOException {
        ChatCompletion input = input(provider.getModelProvider(), "model");
        if (!withSchema) {
            input.getTools().getFirst().setInputSchema(null);
        }
        ObjectMapper mapper = new ObjectMapper();
        ChatModel recording =
                new FileLLMCallRecorder(directory, mapper)
                        .wrap(provider, input, prompt -> toolResponse(PROVIDER_TOOL_CALL_ID));
        // Exercise each provider's actual options conversion, with a deterministic model response.
        recording.call(new Prompt("Weather in Lisbon?", provider.getChatOptions(input)));

        LLMRecording saved = mapper.readValue(recordings().getFirst().toFile(), LLMRecording.class);
        assertEquals(1, saved.entries().getFirst().request().tools().size());
        MockLLM playback = new MockLLM(directory, mapper);
        ChatResponse response =
                playback.getChatModel(input)
                        .call(new Prompt("Weather in Lisbon?", playback.getChatOptions(input)));
        assertEquals(
                WEATHER_TOOL_NAME,
                response.getResult().getOutput().getToolCalls().getFirst().name());

        // A changed tool schema must miss the recording, even when the prompt is identical.
        input.getTools()
                .getFirst()
                .setInputSchema(Map.of("type", "object", "required", List.of("country")));
        assertThrows(
                NonRetryableException.class,
                () ->
                        playback.getChatModel(input)
                                .call(
                                        new Prompt(
                                                "Weather in Lisbon?",
                                                playback.getChatOptions(input))));
    }

    private static Stream<Arguments> providersWithCustomOptions() {
        OkHttpClient client = new OkHttpClient();
        AnthropicConfiguration anthropic = new AnthropicConfiguration();
        anthropic.setApiKey("test-key");
        OpenAIConfiguration openai = new OpenAIConfiguration();
        openai.setApiKey("test-key");
        GeminiVertexConfiguration gemini = new GeminiVertexConfiguration();
        gemini.setApiKey("test-key");
        return Stream.<AIModel>of(
                        new Anthropic(anthropic, client),
                        new OpenAI(openai, client),
                        new GeminiVertex(gemini, client))
                .flatMap(
                        provider ->
                                Stream.of(
                                        Arguments.of(provider, true),
                                        Arguments.of(provider, false)));
    }

    @Test
    void workerRecordsAndFreshContextPlaysBackToolsWithoutARealProvider() throws Exception {
        ChatModel provider =
                prompt ->
                        prompt.getInstructions().stream()
                                        .anyMatch(ToolResponseMessage.class::isInstance)
                                ? textResponse(WEATHER_RESPONSE, "end_turn")
                                : toolResponse(PROVIDER_TOOL_CALL_ID);
        try (AnnotationConfigApplicationContext context = context(true, false, provider)) {
            LLMResponse first = call(context, input(REAL_PROVIDER, "model"));
            ChatCompletion next = input(REAL_PROVIDER, "model");
            addHistory(next, first.getToolCalls().getFirst().getTaskReferenceName());
            assertEquals(WEATHER_RESPONSE, call(context, next).getResult());
        }
        assertEquals(2, recordings().size());
        try (AnnotationConfigApplicationContext context = context(true, true, null)) {
            ChatCompletion followup = input(MockLLM.NAME, "model");
            addHistory(followup, "different-runtime-id");
            assertEquals(WEATHER_RESPONSE, call(context, followup).getResult());
            LLMResponse first = call(context, input(MockLLM.NAME, "model"));
            LLMResponse repeated = call(context, input(MockLLM.NAME, "model"));
            assertNotEquals(
                    PROVIDER_TOOL_CALL_ID, first.getToolCalls().getFirst().getTaskReferenceName());
            assertNotEquals(
                    first.getToolCalls().getFirst().getTaskReferenceName(),
                    repeated.getToolCalls().getFirst().getTaskReferenceName());
            assertEquals(0, first.getTokenUsed());
            assertFalse(
                    context.getBean(MockLLMConfiguration.class)
                            .get()
                            .supportsAssistantPrefill(input(MockLLM.NAME, "model")));
        }
        assertEquals(2, recordings().size(), "Playback must not create recordings");
    }

    @Test
    void parallelRealCallsWriteIndependentFilesAndPlaybackCanRepeatConcurrently() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger calls = new AtomicInteger();
        ChatModel provider =
                prompt -> {
                    calls.incrementAndGet();
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return textResponse("ok", "stop");
                };
        try (AnnotationConfigApplicationContext context = context(true, false, provider);
                ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<LLMResponse> job = () -> call(context, input(REAL_PROVIDER, "model"));
            for (Future<LLMResponse> future : pool.invokeAll(List.of(job, job)))
                assertEquals("ok", future.get().getResult());
        }
        assertEquals(2, calls.get());
        assertEquals(2, recordings().size());
        try (AnnotationConfigApplicationContext context = context(false, true, null);
                ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<LLMResponse>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++)
                jobs.add(() -> call(context, input(MockLLM.NAME, "model")));
            for (Future<LLMResponse> future : pool.invokeAll(jobs))
                assertEquals("ok", future.get().getResult());
        }
    }

    @Test
    void conflictingResponsesAreRecordedButRejectedAtPlaybackStartup() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (AnnotationConfigApplicationContext context =
                context(
                        true,
                        false,
                        prompt -> textResponse("answer " + calls.incrementAndGet(), "stop"))) {
            call(context, input(REAL_PROVIDER, "model"));
            call(context, input(REAL_PROVIDER, "model"));
        }
        assertEquals(2, recordings().size());
        assertThrows(RuntimeException.class, () -> context(false, true, null));
    }

    @Test
    void malformedFilesFailStartupInsteadOfSilentlyDroppingProvider() throws IOException {
        Files.writeString(directory.resolve(INVALID_RECORDING_FILE), INVALID_RECORDING_CONTENT);
        assertThrows(RuntimeException.class, () -> context(false, true, null));
    }

    @Test
    void playbackSkipsNonJsonFiles() throws IOException {
        Files.writeString(directory.resolve("notes.txt"), INVALID_RECORDING_CONTENT);
        try (AnnotationConfigApplicationContext context = context(false, true, null)) {
            assertNotNull(context.getBean(MockLLMConfiguration.class).get());
        }
    }

    @Test
    void recordValidationStillRejectsUnsupportedSchemaVersion() throws IOException {
        Files.writeString(
                directory.resolve(INVALID_RECORDING_FILE),
                "{\"schemaVersion\":3,\"scenario\":\"weather\",\"entries\":[]}");
        assertThrows(RuntimeException.class, () -> context(false, true, null));
    }

    @Test
    void playbackMissFailsWithoutCallingRealProvider() {
        AtomicInteger calls = new AtomicInteger();
        try (AnnotationConfigApplicationContext context =
                context(
                        false,
                        true,
                        prompt -> {
                            calls.incrementAndGet();
                            return textResponse("live", "stop");
                        })) {
            assertThrows(
                    NonRetryableException.class, () -> call(context, input(MockLLM.NAME, "model")));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void invalidJsonIsRecordedBeforeHelperValidationAndFailsAgainDuringPlayback()
            throws IOException {
        try (AnnotationConfigApplicationContext context =
                context(true, false, prompt -> textResponse("not json", "stop"))) {
            ChatCompletion input = input(REAL_PROVIDER, "model");
            input.setJsonOutput(true);
            assertThrows(RuntimeException.class, () -> call(context, input));
        }
        assertEquals(1, recordings().size());
        try (AnnotationConfigApplicationContext context = context(false, true, null)) {
            ChatCompletion input = input(MockLLM.NAME, "model");
            input.setJsonOutput(true);
            RuntimeException failure =
                    assertThrows(RuntimeException.class, () -> call(context, input));
            assertFalse(
                    failure instanceof NonRetryableException, "Must reach helper JSON validation");
        }
    }

    @Test
    void failedProviderAndUnsupportedOptionsDoNotPublishResponses() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (AnnotationConfigApplicationContext context =
                context(
                        true,
                        false,
                        prompt -> {
                            calls.incrementAndGet();
                            throw new IllegalStateException("provider failed");
                        })) {
            assertThrows(
                    IllegalStateException.class,
                    () -> call(context, input(REAL_PROVIDER, "model")));
            ChatCompletion unsupported = input(REAL_PROVIDER, "model");
            unsupported.setWebSearch(true);
            assertThrows(IllegalArgumentException.class, () -> call(context, unsupported));
            assertEquals(1, calls.get());
        }
        assertTrue(recordings().isEmpty());
    }

    @Test
    void recordingPreservesProviderDefaults() throws IOException {
        ChatOptions defaults = ChatOptions.builder().temperature(0.25).build();
        ChatModel provider =
                new ChatModel() {
                    public ChatResponse call(Prompt prompt) {
                        return textResponse("ok", "stop");
                    }

                    public ChatOptions getDefaultOptions() {
                        return defaults;
                    }
                };
        ChatModel wrapped =
                new FileLLMCallRecorder(directory, new ObjectMapper())
                        .wrap(new TestProvider(provider), input(REAL_PROVIDER, "model"), provider);
        assertSame(defaults, wrapped.getDefaultOptions());
    }

    private List<Path> recordings() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".json")).toList();
        }
    }

    private AnnotationConfigApplicationContext context(
            boolean record, boolean playback, ChatModel realModel) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "recording-test",
                                Map.of(
                                        "conductor.integrations.ai.enabled",
                                        "true",
                                        LLMRecordingProperties.RECORD_MODE_PROPERTY,
                                        Boolean.toString(record),
                                        LLMRecordingProperties.ENABLE_LLM_MOCKS_PROPERTY,
                                        Boolean.toString(playback),
                                        LLMRecordingProperties.RECORDINGS_DIRECTORY_PROPERTY,
                                        directory.toString(),
                                        "conductor.file-storage.parentDir",
                                        directory.resolve("payload").toString())));
        context.register(
                LLMRecordingConfiguration.class,
                MockLLMConfiguration.class,
                AIModelProvider.class,
                LLMs.class,
                LLMWorkers.class);
        context.registerBean(OkHttpClient.class, () -> new OkHttpClient());
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(
                SchemaService.class,
                () ->
                        new SchemaService(
                                new InMemorySchemaDAO(),
                                new SchemaCacheProperties(),
                                new JsonSchemaValidator(new ObjectMapper())));
        if (realModel != null)
            context.registerBean(
                    TestProviderConfiguration.class,
                    () -> new TestProviderConfiguration(realModel));
        try {
            context.refresh();
            return context;
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    private static LLMResponse call(
            AnnotationConfigApplicationContext context, ChatCompletion input) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setWorkflowInstanceId("workflow");
        task.setStatus(Task.Status.IN_PROGRESS);
        TaskContext.set(task);
        try {
            return context.getBean(LLMWorkers.class).chatCompletion(input);
        } finally {
            TaskContext.clear();
        }
    }

    static class TestProviderConfiguration implements ModelConfiguration<TestProvider> {
        private final TestProvider provider;

        TestProviderConfiguration(ChatModel model) {
            this.provider = new TestProvider(model);
        }

        public TestProvider get() {
            return provider;
        }

        public void setHttpClient(OkHttpClient client) {}
    }

    static class TestProvider implements AIModel {
        private final ChatModel model;

        TestProvider(ChatModel model) {
            this.model = model;
        }

        public String getModelProvider() {
            return REAL_PROVIDER;
        }

        public ChatModel getChatModel() {
            return model;
        }

        public boolean supportsAssistantPrefill() {
            return false;
        }

        public ImageModel getImageModel() {
            throw new UnsupportedOperationException();
        }

        public List<Float> generateEmbeddings(EmbeddingGenRequest input) {
            throw new UnsupportedOperationException();
        }
    }

    private static ChatCompletion input(String provider, String model) {
        ChatCompletion input = new ChatCompletion();
        input.setLlmProvider(provider);
        input.setModel(model);
        input.setInstructions("Answer weather questions.");
        input.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Weather in Lisbon?"));
        ToolSpec tool = new ToolSpec();
        tool.setName(WEATHER_TOOL_NAME);
        tool.setDescription("Get weather");
        tool.setInputSchema(
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        input.getTools().add(tool);
        return input;
    }

    private static void addHistory(ChatCompletion input, String id) {
        ToolCall call =
                org.conductoross.conductor.ai.model.ToolCall.builder()
                        .taskReferenceName(id)
                        .name(WEATHER_TOOL_NAME)
                        .inputParameters(Map.of("city", "Lisbon"))
                        .output(Map.of("temp_c", 21))
                        .build();
        input.getMessages().add(new ChatMessage(ChatMessage.Role.tool_call, call));
        input.getMessages().add(new ChatMessage(ChatMessage.Role.tool, call));
    }

    private static ChatResponse toolResponse(String id) {
        return new ChatResponse(
                List.of(
                        new Generation(
                                AssistantMessage.builder()
                                        .content(StringUtils.EMPTY)
                                        .toolCalls(
                                                List.of(
                                                        new AssistantMessage.ToolCall(
                                                                id,
                                                                RecordedRequestNormalizer
                                                                        .FUNCTION_TOOL_TYPE,
                                                                WEATHER_TOOL_NAME,
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
