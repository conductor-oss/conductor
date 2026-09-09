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
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.conductoross.conductor.ai.providers.mock.MockLLMModelConfig;
import org.conductoross.conductor.ai.tasks.worker.LLMWorkers;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.dao.schema.InMemorySchemaDAO;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.conductoross.conductor.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class LlmRecordingTest {
    private static final String IGNORED_FILE = "notes.txt";
    private static final String UNSUPPORTED_SCHEMA_RECORDING =
            "{\"schemaVersion\":2,\"scenario\":\"weather\",\"entries\":[]}";

    private static final String BOTH_DISABLED = "false,false";
    private static final String RECORDING_ONLY = "true,false";
    private static final String PLAYBACK_ONLY = "false,true";
    private static final String BOTH_ENABLED = "true,true";
    private static final String MODEL_FIELD = "model";
    private static final String SUCCESS_RESPONSE = "ok";
    private static final String STOP_REASON = "stop";
    private static final String REAL_PROVIDER = "real";
    private static final String WEATHER_RESPONSE = "Sunny";
    private static final String END_TURN_REASON = "end_turn";
    private static final String PROVIDER_TOOL_CALL_ID = "provider-call-id";
    private static final String REPLAY_TOOL_CALL_ID = "different-runtime-id";
    private static final String PLAYBACK_RECORDING_ASSERTION =
            "Playback must not create recordings";
    private static final String ANSWER_PREFIX = "answer ";
    private static final String INVALID_RECORDING_FILE = "broken.json";
    private static final String INVALID_RECORDING_CONTENT = "not JSON";
    private static final String LIVE_RESPONSE = "live";
    private static final String INVALID_JSON_RESPONSE = "not json";
    private static final String JSON_VALIDATION_ASSERTION = "Must reach helper JSON validation";
    private static final String PROVIDER_FAILURE = "provider failed";
    private static final String PROPERTY_SOURCE_NAME = "recording-test";
    private static final String AI_ENABLED_PROPERTY = "conductor.integrations.ai.enabled";
    private static final String ENABLED_VALUE = "true";
    private static final String PAYLOAD_DIRECTORY_PROPERTY = "conductor.file-storage.parentDir";
    private static final String PAYLOAD_DIRECTORY = "payload";
    private static final String WORKFLOW_ID = "workflow";
    private static final String SYSTEM_PROMPT = "Answer weather questions.";
    private static final String USER_PROMPT = "Weather in Lisbon?";
    private static final String WEATHER_TOOL_NAME = "get_weather";
    private static final String WEATHER_TOOL_DESCRIPTION = "Get weather";
    private static final String TYPE_FIELD = "type";
    private static final String OBJECT_TYPE = "object";
    private static final String PROPERTIES_FIELD = "properties";
    private static final String CITY_FIELD = "city";
    private static final String STRING_TYPE = "string";
    private static final String CITY = "Lisbon";
    private static final String TEMPERATURE_FIELD = "temp_c";
    private static final String LISBON_ARGUMENTS_JSON = "{\"city\":\"Lisbon\"}";
    private static final String TOOL_USE_REASON = "tool_use";
    private static final String PROVIDER_RESPONSE_ID = "provider-response-id";
    private static final String PROVIDER_MODEL = "provider-model";

    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({BOTH_DISABLED, RECORDING_ONLY, PLAYBACK_ONLY, BOTH_ENABLED})
    void startupFlagsControlRecorderAndProvider(boolean record, boolean playback) {
        try (AnnotationConfigApplicationContext context = context(record, playback, null)) {
            assertEquals(record ? 1 : 0, context.getBeansOfType(LlmCallRecorder.class).size());
            assertEquals(playback ? 1 : 0, context.getBeansOfType(MockLLMModelConfig.class).size());
            AIModelProvider providers = context.getBean(AIModelProvider.class);
            if (playback)
                assertInstanceOf(
                        MockLLM.class, providers.getModel(input(MockLLM.NAME, MODEL_FIELD)));
            else
                assertThrows(
                        RuntimeException.class,
                        () -> providers.getModel(input(MockLLM.NAME, MODEL_FIELD)));
        }
    }

    @Test
    void disabledRecordingLeavesRealCallsAlone() throws IOException {
        try (AnnotationConfigApplicationContext context =
                context(false, false, prompt -> textResponse(SUCCESS_RESPONSE, STOP_REASON))) {
            assertEquals(
                    SUCCESS_RESPONSE, call(context, input(REAL_PROVIDER, MODEL_FIELD)).getResult());
        }
        assertTrue(recordings().isEmpty());
    }

    @Test
    void workerRecordsAndFreshContextPlaysBackToolsWithoutARealProvider() throws Exception {
        ChatModel provider =
                prompt ->
                        prompt.getInstructions().stream()
                                        .anyMatch(ToolResponseMessage.class::isInstance)
                                ? textResponse(WEATHER_RESPONSE, END_TURN_REASON)
                                : toolResponse(PROVIDER_TOOL_CALL_ID);
        try (AnnotationConfigApplicationContext context = context(true, false, provider)) {
            LLMResponse first = call(context, input(REAL_PROVIDER, MODEL_FIELD));
            ChatCompletion next = input(REAL_PROVIDER, MODEL_FIELD);
            addHistory(next, first.getToolCalls().getFirst().getTaskReferenceName());
            assertEquals(WEATHER_RESPONSE, call(context, next).getResult());
        }
        assertEquals(2, recordings().size());
        try (AnnotationConfigApplicationContext context = context(true, true, null)) {
            ChatCompletion followup = input(MockLLM.NAME, MODEL_FIELD);
            addHistory(followup, REPLAY_TOOL_CALL_ID);
            assertEquals(WEATHER_RESPONSE, call(context, followup).getResult());
            LLMResponse first = call(context, input(MockLLM.NAME, MODEL_FIELD));
            LLMResponse repeated = call(context, input(MockLLM.NAME, MODEL_FIELD));
            assertNotEquals(
                    PROVIDER_TOOL_CALL_ID, first.getToolCalls().getFirst().getTaskReferenceName());
            assertNotEquals(
                    first.getToolCalls().getFirst().getTaskReferenceName(),
                    repeated.getToolCalls().getFirst().getTaskReferenceName());
            assertEquals(0, first.getTokenUsed());
            assertFalse(
                    context.getBean(MockLLMModelConfig.class)
                            .get()
                            .supportsAssistantPrefill(input(MockLLM.NAME, MODEL_FIELD)));
        }
        assertEquals(2, recordings().size(), PLAYBACK_RECORDING_ASSERTION);
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
                    return textResponse(SUCCESS_RESPONSE, STOP_REASON);
                };
        try (AnnotationConfigApplicationContext context = context(true, false, provider);
                ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<LLMResponse> job = () -> call(context, input(REAL_PROVIDER, MODEL_FIELD));
            for (Future<LLMResponse> future : pool.invokeAll(List.of(job, job)))
                assertEquals(SUCCESS_RESPONSE, future.get().getResult());
        }
        assertEquals(2, calls.get());
        assertEquals(2, recordings().size());
        try (AnnotationConfigApplicationContext context = context(false, true, null);
                ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<LLMResponse>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++)
                jobs.add(() -> call(context, input(MockLLM.NAME, MODEL_FIELD)));
            for (Future<LLMResponse> future : pool.invokeAll(jobs))
                assertEquals(SUCCESS_RESPONSE, future.get().getResult());
        }
    }

    @Test
    void conflictingResponsesAreRecordedButRejectedAtPlaybackStartup() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (AnnotationConfigApplicationContext context =
                context(
                        true,
                        false,
                        prompt ->
                                textResponse(
                                        ANSWER_PREFIX + calls.incrementAndGet(), STOP_REASON))) {
            call(context, input(REAL_PROVIDER, MODEL_FIELD));
            call(context, input(REAL_PROVIDER, MODEL_FIELD));
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
        Files.writeString(directory.resolve(IGNORED_FILE), INVALID_RECORDING_CONTENT);
        try (AnnotationConfigApplicationContext context = context(false, true, null)) {
            assertNotNull(context.getBean(MockLLMModelConfig.class).get());
        }
    }

    @Test
    void recordValidationStillRejectsUnsupportedSchemaVersion() throws IOException {
        Files.writeString(directory.resolve(INVALID_RECORDING_FILE), UNSUPPORTED_SCHEMA_RECORDING);
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
                            return textResponse(LIVE_RESPONSE, STOP_REASON);
                        })) {
            assertThrows(
                    NonRetryableException.class,
                    () -> call(context, input(MockLLM.NAME, MODEL_FIELD)));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void invalidJsonIsRecordedBeforeHelperValidationAndFailsAgainDuringPlayback()
            throws IOException {
        try (AnnotationConfigApplicationContext context =
                context(true, false, prompt -> textResponse(INVALID_JSON_RESPONSE, STOP_REASON))) {
            ChatCompletion input = input(REAL_PROVIDER, MODEL_FIELD);
            input.setJsonOutput(true);
            assertThrows(RuntimeException.class, () -> call(context, input));
        }
        assertEquals(1, recordings().size());
        try (AnnotationConfigApplicationContext context = context(false, true, null)) {
            ChatCompletion input = input(MockLLM.NAME, MODEL_FIELD);
            input.setJsonOutput(true);
            RuntimeException failure =
                    assertThrows(RuntimeException.class, () -> call(context, input));
            assertFalse(failure instanceof NonRetryableException, JSON_VALIDATION_ASSERTION);
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
                            throw new IllegalStateException(PROVIDER_FAILURE);
                        })) {
            assertThrows(
                    IllegalStateException.class,
                    () -> call(context, input(REAL_PROVIDER, MODEL_FIELD)));
            ChatCompletion unsupported = input(REAL_PROVIDER, MODEL_FIELD);
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
                        return textResponse(SUCCESS_RESPONSE, STOP_REASON);
                    }

                    public ChatOptions getDefaultOptions() {
                        return defaults;
                    }
                };
        ChatModel wrapped =
                new JsonFileLlmCallRecorder(directory, new ObjectMapper())
                        .wrap(
                                new TestProvider(provider),
                                input(REAL_PROVIDER, MODEL_FIELD),
                                provider);
        assertSame(defaults, wrapped.getDefaultOptions());
    }

    private List<Path> recordings() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(LlmJsonFiles.FILE_EXTENSION)).toList();
        }
    }

    private AnnotationConfigApplicationContext context(
            boolean record, boolean playback, ChatModel realModel) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                PROPERTY_SOURCE_NAME,
                                Map.of(
                                        AI_ENABLED_PROPERTY,
                                        ENABLED_VALUE,
                                        LlmRecordingProperties.RECORD_MODE_PROPERTY,
                                        Boolean.toString(record),
                                        LlmRecordingProperties.ENABLE_LLM_MOCKS_PROPERTY,
                                        Boolean.toString(playback),
                                        LlmRecordingProperties.RECORDINGS_DIRECTORY_PROPERTY,
                                        directory.toString(),
                                        PAYLOAD_DIRECTORY_PROPERTY,
                                        directory.resolve(PAYLOAD_DIRECTORY).toString())));
        context.register(
                LlmRecordingConfiguration.class,
                MockLLMModelConfig.class,
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
        task.setWorkflowInstanceId(WORKFLOW_ID);
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
        input.setInstructions(SYSTEM_PROMPT);
        input.getMessages().add(new ChatMessage(ChatMessage.Role.user, USER_PROMPT));
        ToolSpec tool = new ToolSpec();
        tool.setName(WEATHER_TOOL_NAME);
        tool.setDescription(WEATHER_TOOL_DESCRIPTION);
        tool.setInputSchema(
                Map.of(
                        TYPE_FIELD,
                        OBJECT_TYPE,
                        PROPERTIES_FIELD,
                        Map.of(CITY_FIELD, Map.of(TYPE_FIELD, STRING_TYPE))));
        input.getTools().add(tool);
        return input;
    }

    private static void addHistory(ChatCompletion input, String id) {
        ToolCall call =
                org.conductoross.conductor.ai.model.ToolCall.builder()
                        .taskReferenceName(id)
                        .name(WEATHER_TOOL_NAME)
                        .inputParameters(Map.of(CITY_FIELD, CITY))
                        .output(Map.of(TEMPERATURE_FIELD, 21))
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
                                                                LlmRequestResponseConverter
                                                                        .FUNCTION_TOOL_TYPE,
                                                                WEATHER_TOOL_NAME,
                                                                LISBON_ARGUMENTS_JSON)))
                                        .build(),
                                ChatGenerationMetadata.builder()
                                        .finishReason(TOOL_USE_REASON)
                                        .build())));
    }

    private static ChatResponse textResponse(String text, String finish) {
        return new ChatResponse(
                List.of(
                        new Generation(
                                new AssistantMessage(text),
                                ChatGenerationMetadata.builder().finishReason(finish).build())),
                ChatResponseMetadata.builder()
                        .id(PROVIDER_RESPONSE_ID)
                        .model(PROVIDER_MODEL)
                        .usage(new DefaultUsage(12, 13, 25))
                        .build());
    }
}
