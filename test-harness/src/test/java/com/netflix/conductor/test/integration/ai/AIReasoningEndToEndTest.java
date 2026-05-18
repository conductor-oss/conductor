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
package com.netflix.conductor.test.integration.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.ModelConfiguration;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the AI reasoning surface. A workflow with a {@code LLM_CHAT_COMPLETE}
 * task runs against an in-process fake provider whose {@link ChatModel} returns a canned response
 * carrying {@code metadata["reasoning"]} and {@code metadata["reasoning_tokens"]}. The assertion
 * walks the full pipeline that production traffic takes — task scheduling → mapper →
 * AnnotatedSystemTaskWorker → {@code LLMs.chatComplete} → {@code LLMHelper} → {@code LLMResponse} —
 * and confirms the {@code reasoning} and {@code reasoningTokens} fields appear on the task output
 * exactly as a real provider would surface them.
 *
 * <p>Why a fake provider rather than real OpenAI/Anthropic/Gemini: this test is for CI and must be
 * deterministic and offline. The reasoning round-trip is already exercised against live APIs by
 * {@code AIModelIntegrationTest} in the AI module; this test is concerned with the Conductor
 * task/worker plumbing around it.
 */
@SpringBootTest(classes = {ConductorTestApp.class, AIReasoningEndToEndTest.FakeAITestConfig.class})
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.app.sweeper.queuePopTimeout=750",
            // Required to activate every AI-module component scanned by
            // ConductorTestApp's basePackages.
            "conductor.integrations.ai.enabled=true"
        })
class AIReasoningEndToEndTest {

    private static final String FAKE_PROVIDER = "fake_reasoning_llm";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "ai-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "ai-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
    }

    @Autowired private MetadataService metadataService;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private AIModelProvider aiModelProvider;
    @Autowired private FakeChatModel fakeChatModel;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    // The default SystemTaskRegistry is built from Spring-managed WorkflowSystemTask beans only —
    // it does not include the AnnotatedWorkflowSystemTask instances that
    // WorkerTaskAnnotationScanner
    // creates dynamically for @WorkerTask methods. The mutable ``asyncSystemTasks`` Set is the one
    // those annotated tasks land in, so we look up LLM_CHAT_COMPLETE there.
    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private java.util.Set<WorkflowSystemTask> asyncSystemTasks;

    @BeforeEach
    void resetFakeBetweenRuns() {
        fakeChatModel.reset();
    }

    @Test
    void chatCompleteSurfacesReasoningOnTaskOutput() {
        fakeChatModel.stageResponse(
                "The answer is 7.",
                "First, count the apples. Then divide by 2. Then add 1.",
                123,
                "resp_fake_e2e_1");

        String wfName = "ai_reasoning_e2e_" + UUID.randomUUID();
        registerWorkflow(wfName);

        Map<String, Object> input = new HashMap<>();
        input.put("llmProvider", FAKE_PROVIDER);
        input.put("model", "fake-reasoning-1");
        // reasoningSummary is the universal opt-in flag; the fake model honors
        // it by returning canned reasoning we staged.
        input.put("reasoningSummary", "auto");
        input.put("instructions", "Solve the math problem.");
        input.put(
                "userInput",
                "If I have 14 apples and split them in half then add 1, what do I have?");

        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(wfName);
        swi.setVersion(1);
        swi.setWorkflowInput(input);

        String workflowId = workflowExecutor.startWorkflow(swi);

        Workflow completed = awaitWorkflow(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals(1, completed.getTasks().size(), "expected one LLM_CHAT_COMPLETE task");

        Map<String, Object> output = completed.getTasks().get(0).getOutputData();
        assertNotNull(output);
        assertEquals(
                "First, count the apples. Then divide by 2. Then add 1.", output.get("reasoning"));
        assertEquals(
                123,
                ((Number) output.get("reasoningTokens")).intValue(),
                "reasoning token count must propagate through LLMResponse to task output");
        assertEquals(
                "resp_fake_e2e_1",
                output.get("responseId"),
                "responseId must propagate so previousResponseId chaining works in real loops");
        // The visible answer is still surfaced in the result field, untouched
        // by the reasoning plumbing.
        Object result = output.get("result");
        assertTrue(
                result != null
                        && (result.toString().contains("The answer is 7.")
                                || result.toString().contains("answer is 7")),
                "the visible message text must remain on the task output: " + result);
    }

    @Test
    void chatCompleteWithNoReasoning_outputOmitsReasoningFields() {
        // Symmetric case: provider returned no reasoning metadata (the model
        // doesn't think, or the caller didn't opt in). The task output must
        // not carry stale or empty reasoning fields.
        fakeChatModel.stageResponse("Plain response.", null, null, "resp_fake_e2e_plain");

        String wfName = "ai_no_reasoning_e2e_" + UUID.randomUUID();
        registerWorkflow(wfName);

        Map<String, Object> input = new HashMap<>();
        input.put("llmProvider", FAKE_PROVIDER);
        input.put("model", "fake-no-reasoning");
        input.put("instructions", "Say hi.");
        input.put("userInput", "Hi.");

        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(wfName);
        swi.setVersion(1);
        swi.setWorkflowInput(input);

        String workflowId = workflowExecutor.startWorkflow(swi);
        Workflow completed = awaitWorkflow(workflowId);

        Map<String, Object> output = completed.getTasks().get(0).getOutputData();
        assertNotNull(output);
        assertNull(
                output.get("reasoning"),
                "no reasoning emitted ⇒ reasoning must be absent on task output");
        assertNull(
                output.get("reasoningTokens"),
                "no reasoning_tokens emitted ⇒ reasoningTokens must be absent");
    }

    @Test
    void doWhileLoopDoesNotAutoThreadPreviousResponseId() {
        // Auto-threading of previousResponseId between same-refName loop
        // iterations is intentionally DISABLED — see the javadoc on
        // AIModelTaskMapper.threadPreviousResponseId for the OpenAI token
        // billing failure mode that drove the decision. This test locks in
        // that contract end-to-end: iteration 2 must NOT inherit iteration
        // 1's responseId unless the workflow definition wires it explicitly.
        fakeChatModel.stageResponse("Turn one.", "Thinking turn 1.", 10, "resp_turn_1");
        fakeChatModel.stageResponse("Turn two.", "Thinking turn 2.", 11, "resp_turn_2");
        AtomicReference<String> turn2PreviousId = new AtomicReference<>();
        fakeChatModel.onCall(
                (callIndex, opts) -> {
                    if (callIndex == 1 && opts instanceof FakeChatOptions f) {
                        turn2PreviousId.set(f.previousResponseId);
                    }
                });

        String wfName = "ai_loop_e2e_" + UUID.randomUUID();
        registerLoopWorkflow(wfName);

        Map<String, Object> input = new HashMap<>();
        input.put("llmProvider", FAKE_PROVIDER);
        input.put("model", "fake-loop");
        input.put("instructions", "Iterate.");
        input.put("userInput", "Iterate.");

        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(wfName);
        swi.setVersion(1);
        swi.setWorkflowInput(input);

        String workflowId = workflowExecutor.startWorkflow(swi);
        Workflow completed = awaitWorkflow(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        assertNull(
                turn2PreviousId.get(),
                "loop iterations must NOT auto-thread previousResponseId — re-enable only after the history rebuild emits a strict delta");
    }

    // -- workflow registration helpers --

    private void registerWorkflow(String name) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("LLM_CHAT_COMPLETE");
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(60);
        try {
            metadataService.registerTaskDef(List.of(taskDef));
        } catch (Exception ignore) {
            // already registered from a prior test
        }

        WorkflowTask task = new WorkflowTask();
        task.setName("LLM_CHAT_COMPLETE");
        task.setTaskReferenceName("chat");
        task.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("llmProvider", "${workflow.input.llmProvider}");
        taskInput.put("model", "${workflow.input.model}");
        taskInput.put("reasoningSummary", "${workflow.input.reasoningSummary}");
        taskInput.put("instructions", "${workflow.input.instructions}");
        taskInput.put("userInput", "${workflow.input.userInput}");
        task.setInputParameters(taskInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private void registerLoopWorkflow(String name) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("LLM_CHAT_COMPLETE");
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(60);
        try {
            metadataService.registerTaskDef(List.of(taskDef));
        } catch (Exception ignore) {
        }
        TaskDef loopDef = new TaskDef();
        loopDef.setName("DO_WHILE");
        loopDef.setRetryCount(0);
        try {
            metadataService.registerTaskDef(List.of(loopDef));
        } catch (Exception ignore) {
        }

        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName("chat");
        chat.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("llmProvider", "${workflow.input.llmProvider}");
        taskInput.put("model", "${workflow.input.model}");
        taskInput.put("instructions", "${workflow.input.instructions}");
        taskInput.put("userInput", "${workflow.input.userInput}");
        chat.setInputParameters(taskInput);

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        // run exactly two iterations
        loop.setLoopCondition("if ( $.loop['iteration'] < 2 ) { true; } else { false; }");
        loop.setLoopOver(List.of(chat));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(loop));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private Workflow awaitWorkflow(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            // System task workers are disabled in test mode
                            // (``conductor.system-task-workers.enabled=false``),
                            // so we manually drain any LLM_CHAT_COMPLETE tasks
                            // queued by the decider and execute them via
                            // AsyncSystemTaskExecutor — the same path the
                            // SystemTaskWorkerCoordinator would take in
                            // production.
                            drainPendingChatTasks();
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            return wf != null
                                    && wf.getStatus() != null
                                    && wf.getStatus().isTerminal();
                        });
        return latest.get();
    }

    private void drainPendingChatTasks() {
        WorkflowSystemTask chatTask =
                asyncSystemTasks.stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "LLM_CHAT_COMPLETE WorkflowSystemTask was never"
                                                        + " registered — the AI module's"
                                                        + " WorkerTaskAnnotationScanner must run before"
                                                        + " this test executes."));
        List<String> taskIds = queueDAO.pop("LLM_CHAT_COMPLETE", 5, 100);
        for (String taskId : taskIds) {
            asyncSystemTaskExecutor.execute(chatTask, taskId);
        }
    }

    // -- Test wiring --

    @TestConfiguration
    static class FakeAITestConfig {

        @Bean
        FakeChatModel fakeChatModel() {
            return new FakeChatModel();
        }

        @Bean
        ModelConfiguration<FakeAIModel> fakeAIModelConfiguration(FakeChatModel fakeChatModel) {
            return () -> new FakeAIModel(fakeChatModel);
        }
    }

    /**
     * The {@link AIModel} the test wires into {@link AIModelProvider} keyed under FAKE_PROVIDER.
     */
    static class FakeAIModel implements AIModel {

        private final FakeChatModel chatModel;

        FakeAIModel(FakeChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public String getModelProvider() {
            return FAKE_PROVIDER;
        }

        @Override
        public ChatModel getChatModel() {
            return chatModel;
        }

        @Override
        public ChatOptions getChatOptions(ChatCompletion input) {
            // Use a custom options type so the fake chat model can see the
            // previousResponseId that AIModelTaskMapper auto-threaded.
            FakeChatOptions opts = new FakeChatOptions();
            opts.model = input.getModel();
            opts.previousResponseId = input.getPreviousResponseId();
            opts.reasoningSummary = input.getReasoningSummary();
            return opts;
        }

        @Override
        public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ImageModel getImageModel() {
            throw new UnsupportedOperationException();
        }
    }

    /** Minimal {@link ChatOptions} that carries the fields the fake cares about. */
    static class FakeChatOptions implements ChatOptions {
        String model;
        String previousResponseId;
        String reasoningSummary;

        @Override
        public String getModel() {
            return model;
        }

        @Override
        public Double getFrequencyPenalty() {
            return null;
        }

        @Override
        public Integer getMaxTokens() {
            return 1024;
        }

        @Override
        public Double getPresencePenalty() {
            return null;
        }

        @Override
        public List<String> getStopSequences() {
            return List.of();
        }

        @Override
        public Double getTemperature() {
            return null;
        }

        @Override
        public Integer getTopK() {
            return null;
        }

        @Override
        public Double getTopP() {
            return null;
        }

        @Override
        public ChatOptions copy() {
            FakeChatOptions c = new FakeChatOptions();
            c.model = model;
            c.previousResponseId = previousResponseId;
            c.reasoningSummary = reasoningSummary;
            return c;
        }
    }

    /**
     * Fake {@link ChatModel}: returns the next staged response on each call and exposes the options
     * it was called with via the {@link #onCall(java.util.function.BiConsumer)} hook so
     * loop-threading invariants can be asserted.
     */
    static class FakeChatModel implements ChatModel {
        private final List<StagedResponse> staged = new ArrayList<>();
        private int callIndex;
        private java.util.function.BiConsumer<Integer, ChatOptions> onCallHook;

        void reset() {
            staged.clear();
            callIndex = 0;
            onCallHook = null;
        }

        void stageResponse(
                String text, String reasoning, Integer reasoningTokens, String responseId) {
            staged.add(new StagedResponse(text, reasoning, reasoningTokens, responseId));
        }

        void onCall(java.util.function.BiConsumer<Integer, ChatOptions> hook) {
            this.onCallHook = hook;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            if (onCallHook != null) {
                onCallHook.accept(callIndex, prompt.getOptions());
            }
            StagedResponse next = staged.get(Math.min(callIndex, staged.size() - 1));
            callIndex++;
            ChatResponseMetadata.Builder meta =
                    ChatResponseMetadata.builder()
                            .id(next.responseId)
                            .usage(new DefaultUsage(5, 10, 15));
            if (next.responseId != null) {
                // LLMHelper reads ``response_id`` from the metadata map (not from
                // ChatResponseMetadata.id) — match what real provider adapters do
                // so the responseId actually surfaces through to LLMResponse.
                meta.keyValue("response_id", next.responseId);
            }
            if (next.reasoning != null && !next.reasoning.isBlank()) {
                meta.keyValue("reasoning", next.reasoning);
            }
            if (next.reasoningTokens != null) {
                meta.keyValue("reasoning_tokens", next.reasoningTokens);
            }
            ChatGenerationMetadata genMeta =
                    ChatGenerationMetadata.builder().finishReason("STOP").build();
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(next.text), genMeta)),
                    meta.build());
        }
    }

    private record StagedResponse(
            String text, String reasoning, Integer reasoningTokens, String responseId) {}
}
