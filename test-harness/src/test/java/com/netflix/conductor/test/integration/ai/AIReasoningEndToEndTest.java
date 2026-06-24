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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the AI reasoning surface against real providers.
 *
 * <p>Each nested test class is gated on an environment variable so the suite is safe to run in CI
 * with all keys, or locally with only one. Tests are skipped (not failed) when the key is absent.
 *
 * <p>Required environment variables:
 *
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} — enables {@link AnthropicTests}
 *   <li>{@code OPENAI_API_KEY} — enables {@link OpenAITests}
 * </ul>
 *
 * <p>Optional overrides:
 *
 * <ul>
 *   <li>{@code ANTHROPIC_THINKING_MODEL} — model with extended thinking support (default: {@code
 *       claude-sonnet-4-6})
 *   <li>{@code ANTHROPIC_MODEL} — lightweight model without thinking (default: {@code
 *       claude-haiku-4-5})
 *   <li>{@code OPENAI_MODEL} — chat model for OpenAI tests (default: {@code gpt-4o-mini})
 * </ul>
 *
 * <p>Assertions are intentionally coarser than unit tests: we verify that fields are present or
 * absent on task output and that the mapper-resolved {@code messages} array (stored on {@code
 * task.getInputData()}) has the correct shape — not exact LLM response content, which is
 * non-deterministic.
 */
@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.app.sweeper.queuePopTimeout=750",
            "conductor.integrations.ai.enabled=true"
        })
class AIReasoningEndToEndTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
        // AI provider keys — when absent the provider bean is created with an empty key and
        // will only fail on actual use, which the @EnabledIfEnvironmentVariable guards prevent.
        registry.add(
                "conductor.ai.anthropic.apiKey",
                () -> System.getenv().getOrDefault("ANTHROPIC_API_KEY", ""));
        registry.add(
                "conductor.ai.openai.apiKey",
                () -> System.getenv().getOrDefault("OPENAI_API_KEY", ""));
    }

    @Autowired private MetadataService metadataService;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private java.util.Set<WorkflowSystemTask> asyncSystemTasks;

    // ── Anthropic ──────────────────────────────────────────────────────────

    /**
     * Tests against the Anthropic provider.
     *
     * <p>Anthropic-specific behaviour exercised here:
     *
     * <ul>
     *   <li>Extended thinking ({@code thinkingTokenLimit > 0}) surfaces as {@code reasoning} on
     *       task output; Anthropic does NOT emit a separate {@code reasoning_tokens} counter.
     *   <li>{@code supportsAssistantPrefill() = false}: the mapper must not inject a trailing
     *       assistant message into a DO_WHILE loop body's messages array — if it did, Anthropic
     *       4.6+ would reject the call with HTTP 400 and the workflow would fail.
     * </ul>
     */
    @Nested
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    class AnthropicTests {

        private static final String PROVIDER = "anthropic";

        private static final String THINKING_MODEL =
                System.getenv().getOrDefault("ANTHROPIC_THINKING_MODEL", "claude-sonnet-4-6");

        private static final String BASIC_MODEL =
                System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-4-5");

        @Test
        void chatCompleteSurfacesReasoningOnTaskOutput() {
            String wfName = "ai_reasoning_e2e_" + UUID.randomUUID();
            registerWorkflowWithThinking(wfName);

            Map<String, Object> input = new HashMap<>();
            input.put("llmProvider", PROVIDER);
            input.put("model", THINKING_MODEL);
            input.put("reasoningSummary", "auto");
            // Anthropic's enabled-thinking path requires budget_tokens >= 1024.
            input.put("thinkingTokenLimit", 2000);
            input.put("instructions", "Think step by step.");
            input.put("userInput", "What is 2 + 2?");

            Workflow completed = awaitWorkflow(startWorkflow(wfName, 1, input));
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            Map<String, Object> output = completed.getTasks().get(0).getOutputData();
            assertNotNull(output);
            assertNotNull(
                    output.get("reasoning"),
                    "Anthropic extended thinking must appear as 'reasoning' on task output when "
                            + "thinkingTokenLimit > 0 and reasoningSummary is set");
            // Anthropic bills extended thinking under output_tokens, not a separate counter.
            // The field must be absent — not zero — so callers cannot misread 0 as "reasoning
            // ran but was cheap".
            assertNull(
                    output.get("reasoningTokens"),
                    "Anthropic does not surface reasoning_tokens; field must be absent on output");
        }

        @Test
        void chatCompleteWithNoReasoningOmitsFields() {
            String wfName = "ai_no_reasoning_e2e_" + UUID.randomUUID();
            registerWorkflow(wfName);

            Map<String, Object> input = new HashMap<>();
            input.put("llmProvider", PROVIDER);
            input.put("model", BASIC_MODEL);
            input.put("instructions", "Answer briefly.");
            input.put("userInput", "What is 2 + 2?");

            Workflow completed = awaitWorkflow(startWorkflow(wfName, 1, input));
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            Map<String, Object> output = completed.getTasks().get(0).getOutputData();
            assertNotNull(output);
            assertNull(output.get("reasoning"), "no thinking requested → reasoning must be absent");
            assertNull(
                    output.get("reasoningTokens"),
                    "no thinking requested → reasoningTokens must be absent");
        }

        @Test
        void loopHistoryInjectionIsSuppressedWhenProviderRejectsPrefill() {
            // Anthropic returns supportsAssistantPrefill()=false. If the mapper
            // incorrectly appended the iter-1 assistant message into iter-2's messages,
            // Anthropic 4.6+ would reject with HTTP 400 "This model does not support
            // assistant message prefill." The workflow completing COMPLETED is the
            // primary proof the suppression works; the messages assertion confirms it
            // at the mapper level via task.getInputData(), which is stored before the
            // provider call.
            String wfName = "ai_loop_prefill_off_e2e_" + UUID.randomUUID();
            registerLoopWorkflow(wfName);

            Workflow completed = runLoopWorkflow(wfName, PROVIDER, BASIC_MODEL);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            List<Map<String, Object>> iter2Messages =
                    messagesForLoopIteration(completed, "chat", 2);
            assertTrue(
                    iter2Messages.stream().noneMatch(m -> "assistant".equals(m.get("role"))),
                    "iteration 2 must NOT contain a trailing assistant message when the provider "
                            + "rejects prefill; saw "
                            + iter2Messages);
            assertTrue(
                    iter2Messages.stream().anyMatch(m -> "user".equals(m.get("role"))),
                    "iteration 2 must still carry the user message; saw " + iter2Messages);
        }

        @Test
        void explicitPreviousResponseIdSuppressesLoopHistoryInjection() {
            // A non-blank previousResponseId on the chat task must suppress the
            // mapper's history injection regardless of whether the provider itself
            // uses the field. Anthropic ignores previousResponseId at the API level,
            // so this test isolates mapper behaviour without triggering a provider
            // error on an unrecognised ID.
            String wfName = "ai_loop_explicit_prev_e2e_" + UUID.randomUUID();
            registerLoopWorkflowWithPreviousResponseId(wfName);

            Workflow completed = runLoopWorkflow(wfName, PROVIDER, BASIC_MODEL);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            List<Map<String, Object>> iter2Messages =
                    messagesForLoopIteration(completed, "chat", 2);
            assertTrue(
                    iter2Messages.stream().noneMatch(m -> "assistant".equals(m.get("role"))),
                    "previousResponseId set → assistant history injection must be suppressed; saw "
                            + iter2Messages);
        }
    }

    // ── OpenAI ────────────────────────────────────────────────────────────

    /**
     * Tests against the OpenAI provider (Responses API).
     *
     * <p>OpenAI-specific behaviour exercised here:
     *
     * <ul>
     *   <li>{@code supportsAssistantPrefill() = true} (the default): the mapper IS expected to
     *       inject the prior iteration's output as an assistant message into a DO_WHILE loop body.
     *   <li>The Responses API returns a {@code response_id} in every reply; that ID must appear as
     *       {@code responseId} on task output so callers can thread multi-turn conversations.
     * </ul>
     */
    @Nested
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    class OpenAITests {

        private static final String PROVIDER = "openai";

        private static final String MODEL =
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");

        @Test
        void loopHistoryInjectionStillRunsWhenProviderAcceptsPrefill() {
            // OpenAI accepts assistant-message prefill, so the mapper should inject
            // iteration 1's output as an assistant turn into iteration 2's messages.
            // The assertion reads from task.getInputData() — what the mapper persisted
            // before the provider call — so it reflects mapper behaviour directly.
            String wfName = "ai_loop_prefill_on_e2e_" + UUID.randomUUID();
            registerLoopWorkflow(wfName);

            Workflow completed = runLoopWorkflow(wfName, PROVIDER, MODEL);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            List<Map<String, Object>> iter2Messages =
                    messagesForLoopIteration(completed, "chat", 2);
            assertTrue(
                    iter2Messages.stream().anyMatch(m -> "assistant".equals(m.get("role"))),
                    "iteration 2 must carry iteration 1's output as an assistant message when the "
                            + "provider accepts prefill; saw "
                            + iter2Messages);
        }

        @Test
        void responseIdIsSurfacedOnTaskOutput() {
            // OpenAI Responses API includes a response_id on every reply. LLMHelper
            // reads it from ChatResponseMetadata["response_id"] and sets it on
            // LLMResponse.responseId, which the task worker writes to outputData.
            String wfName = "ai_response_id_e2e_" + UUID.randomUUID();
            registerWorkflow(wfName);

            Map<String, Object> input = new HashMap<>();
            input.put("llmProvider", PROVIDER);
            input.put("model", MODEL);
            input.put("instructions", "Answer briefly.");
            input.put("userInput", "What is 2 + 2?");

            Workflow completed = awaitWorkflow(startWorkflow(wfName, 1, input));
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

            Map<String, Object> output = completed.getTasks().get(0).getOutputData();
            assertNotNull(output);
            assertNotNull(
                    output.get("responseId"),
                    "OpenAI Responses API must surface response_id on task output for caller "
                            + "chaining");
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private String startWorkflow(String name, int version, Map<String, Object> input) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(name);
        swi.setVersion(version);
        swi.setWorkflowInput(input);
        return workflowExecutor.startWorkflow(swi);
    }

    private Workflow runLoopWorkflow(String wfName, String provider, String model) {
        Map<String, Object> input = new HashMap<>();
        input.put("llmProvider", provider);
        input.put("model", model);
        input.put("instructions", "Answer in one sentence.");
        input.put("userInput", "What is 2 + 2?");
        return awaitWorkflow(startWorkflow(wfName, 1, input));
    }

    private Workflow awaitWorkflow(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(120, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            // System task workers are disabled in test mode
                            // (conductor.system-task-workers.enabled=false), so we
                            // manually drain any LLM_CHAT_COMPLETE tasks queued by
                            // the decider and execute them via AsyncSystemTaskExecutor
                            // — the same path SystemTaskWorkerCoordinator takes in
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
                                                        + " WorkerTaskAnnotationScanner must run"
                                                        + " before this test executes."));
        List<String> taskIds = queueDAO.pop("LLM_CHAT_COMPLETE", 5, 100);
        for (String taskId : taskIds) {
            asyncSystemTaskExecutor.execute(chatTask, taskId);
        }
    }

    /**
     * Walks {@link Workflow#getTasks()} to find the LLM_CHAT_COMPLETE task that ran as the given
     * iteration of the named loop body, then returns its mapper-resolved {@code messages} input —
     * i.e. exactly what the task mapper wrote into {@code task.getInputData()} before the worker
     * called the provider. Reading at this layer lets assertions speak to mapper behaviour
     * directly, independent of any downstream normalisation in {@code LLMHelper}.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messagesForLoopIteration(
            Workflow workflow, String loopBodyRef, int iteration) {
        return workflow.getTasks().stream()
                .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getTaskType()))
                .filter(t -> t.getIteration() == iteration)
                .filter(
                        t ->
                                t.getReferenceTaskName() != null
                                        && t.getReferenceTaskName().startsWith(loopBodyRef))
                .findFirst()
                .map(t -> (List<Map<String, Object>>) t.getInputData().get("messages"))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "no LLM_CHAT_COMPLETE task found for ref '"
                                                + loopBodyRef
                                                + "' iteration "
                                                + iteration
                                                + "; tasks: "
                                                + workflow.getTasks()));
    }

    // ── Workflow registration ──────────────────────────────────────────────

    private void registerWorkflow(String name) {
        ensureTaskDef("LLM_CHAT_COMPLETE");

        WorkflowTask task = new WorkflowTask();
        task.setName("LLM_CHAT_COMPLETE");
        task.setTaskReferenceName("chat");
        task.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("llmProvider", "${workflow.input.llmProvider}");
        taskInput.put("model", "${workflow.input.model}");
        taskInput.put("instructions", "${workflow.input.instructions}");
        taskInput.put("userInput", "${workflow.input.userInput}");
        task.setInputParameters(taskInput);

        metadataService.updateWorkflowDef(List.of(singleTaskWorkflow(name, task)));
    }

    private void registerWorkflowWithThinking(String name) {
        ensureTaskDef("LLM_CHAT_COMPLETE");

        WorkflowTask task = new WorkflowTask();
        task.setName("LLM_CHAT_COMPLETE");
        task.setTaskReferenceName("chat");
        task.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("llmProvider", "${workflow.input.llmProvider}");
        taskInput.put("model", "${workflow.input.model}");
        taskInput.put("reasoningSummary", "${workflow.input.reasoningSummary}");
        taskInput.put("thinkingTokenLimit", "${workflow.input.thinkingTokenLimit}");
        taskInput.put("instructions", "${workflow.input.instructions}");
        taskInput.put("userInput", "${workflow.input.userInput}");
        task.setInputParameters(taskInput);

        metadataService.updateWorkflowDef(List.of(singleTaskWorkflow(name, task)));
    }

    private void registerLoopWorkflow(String name) {
        ensureTaskDef("LLM_CHAT_COMPLETE");

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

        metadataService.updateWorkflowDef(List.of(twoIterationLoopWorkflow(name, chat)));
    }

    private void registerLoopWorkflowWithPreviousResponseId(String name) {
        ensureTaskDef("LLM_CHAT_COMPLETE");

        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName("chat");
        chat.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("llmProvider", "${workflow.input.llmProvider}");
        taskInput.put("model", "${workflow.input.model}");
        taskInput.put("instructions", "${workflow.input.instructions}");
        taskInput.put("userInput", "${workflow.input.userInput}");
        // Constant on every iteration — any non-blank previousResponseId triggers
        // the mapper's suppression branch. The value need not be a valid provider
        // response ID for providers that ignore the field (e.g. Anthropic).
        taskInput.put("previousResponseId", "resp_explicit_loop_threading_test");
        chat.setInputParameters(taskInput);

        metadataService.updateWorkflowDef(List.of(twoIterationLoopWorkflow(name, chat)));
    }

    private void ensureTaskDef(String taskType) {
        TaskDef td = new TaskDef();
        td.setName(taskType);
        td.setRetryCount(0);
        td.setTimeoutSeconds(120);
        try {
            metadataService.registerTaskDef(List.of(td));
        } catch (Exception ignored) {
        }
    }

    private WorkflowDef singleTaskWorkflow(String name, WorkflowTask task) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(task));
        return def;
    }

    private WorkflowDef twoIterationLoopWorkflow(String name, WorkflowTask body) {
        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        loop.setLoopCondition("if ( $.loop['iteration'] < 2 ) { true; } else { false; }");
        loop.setLoopOver(List.of(body));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(loop));
        return def;
    }
}
