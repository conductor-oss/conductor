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
package com.netflix.conductor.test.integration.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the <b>real</b> AgentSpan registration/compilation pipeline — not the
 * hand-rolled {@code metadata.agentDef}-stamped {@link WorkflowDef}s in {@link
 * ConductorAgentEndToEndTest}. Every agent here is registered through {@code
 * AgentService.deploy(AgentStartRequest)} (the same call {@code POST /api/agent/deploy} makes) with
 * a genuine {@link AgentConfig}, compiled by {@code AgentCompiler}/{@code ToolCompiler} into an
 * LLM-reasoning-loop {@link WorkflowDef} ({@code LLM_CHAT_COMPLETE} + dynamic tool dispatch), then
 * driven to completion by a real model.
 *
 * <p>Requires a live LLM: skipped (not failed — see {@link #requireLlmProvider()}) unless {@code
 * ANTHROPIC_API_KEY} or {@code OPENAI_API_KEY} is set, mirroring {@code AIReasoningEndToEndTest}'s
 * gating. Picks whichever key is present (Anthropic preferred) rather than running the
 * provider-agnostic AgentSpan pipeline twice — these tests exercise the compiler/dispatch
 * machinery, not provider-specific quirks, so there is nothing to gain from a two-provider matrix,
 * only 2x the LLM spend on every CI run once a key is configured.
 *
 * <p>LLM output wording is inherently non-deterministic, so assertions favor <b>structural</b>
 * proof (a real {@code HTTP} task ran with {@code statusCode=200}; a real {@code HUMAN} task paused
 * and resumed; the compiled agent's {@code metadata.agentDef} carries a full {@link AgentConfig})
 * over exact text matching.
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
            "conductor.integrations.ai.enabled=true",
            "agentspan.embedded=true"
        })
class AgentSpanRegistrationEndToEndTest {

    private static final boolean HAS_ANTHROPIC_KEY =
            System.getenv("ANTHROPIC_API_KEY") != null
                    && !System.getenv("ANTHROPIC_API_KEY").isBlank();
    private static final boolean HAS_OPENAI_KEY =
            System.getenv("OPENAI_API_KEY") != null && !System.getenv("OPENAI_API_KEY").isBlank();

    private static final String MODEL =
            HAS_ANTHROPIC_KEY
                    ? "anthropic/"
                            + System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-4-5")
                    // gpt-4o-mini did not reliably follow the multi-tool "you must ask via
                    // ask_question" instruction in this agentic, multi-turn shape (it sometimes
                    // answered directly without calling any tool) -- gpt-4o complies reliably.
                    : "openai/" + System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o");

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
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "agentspan-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "agentspan-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
        // Empty when absent — the provider bean tolerates a blank key and only fails on actual
        // use, which requireLlmProvider() below prevents.
        registry.add(
                "conductor.ai.anthropic.apiKey",
                () -> System.getenv().getOrDefault("ANTHROPIC_API_KEY", ""));
        registry.add(
                "conductor.ai.openai.apiKey",
                () -> System.getenv().getOrDefault("OPENAI_API_KEY", ""));
    }

    @Autowired private AgentService agentService;
    @Autowired private MetadataService metadataService;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private java.util.Set<WorkflowSystemTask> asyncSystemTasks;

    @BeforeEach
    void requireLlmProvider() {
        Assumptions.assumeTrue(
                HAS_ANTHROPIC_KEY || HAS_OPENAI_KEY,
                "Skipping: requires ANTHROPIC_API_KEY or OPENAI_API_KEY to drive a real LLM"
                        + " through the AgentSpan compiler.");
    }

    @Test
    void helloWorldAgentRunsThroughRealAgentSpanCompiler() {
        String agentName = "hello_world_agentspan_e2e_" + UUID.randomUUID();
        deployAgent(
                AgentConfig.builder()
                        .name(agentName)
                        .model(MODEL)
                        .instructions(
                                "You are a friendly assistant. Greet the user warmly and briefly"
                                        + " acknowledge what they said.")
                        .maxTurns(1)
                        .build());

        // The compiled WorkflowDef IS the agent registration — metadata.agentDef must carry the
        // full AgentConfig (model/instructions/...), not just a bare name like the hand-rolled
        // agents in ConductorAgentEndToEndTest.
        WorkflowDef compiled = metadataService.getWorkflowDef(agentName, null);
        assertTrue(compiled.isAgent());
        assertEquals("agent", compiled.getMetadata().get("classifier"));
        assertEquals("conductor", compiled.getMetadata().get("agent_sdk"));
        @SuppressWarnings("unchecked")
        Map<String, Object> agentDef = (Map<String, Object>) compiled.getMetadata().get("agentDef");
        assertEquals(MODEL, agentDef.get("model"));

        String wfName = "call_agentspan_hello_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, "Hi there, this is a real AgentSpan test.");
        String workflowId = startWorkflow(wfName, new HashMap<>());

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task chat = agentTaskOf(completed);
        assertEquals("completed", chat.getOutputData().get("state"));
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chat.getOutputData().get("output");
        assertNotNull(output, "a real LLM_CHAT_COMPLETE agent must surface a structured output");
        assertTrue(
                output.get("result") instanceof String
                        && !((String) output.get("result")).isBlank(),
                "real LLM response text must be non-blank: " + output);
    }

    /**
     * Proves a real, LLM-decided (not hardcoded) HTTP tool call: the agent is told to fetch a
     * "lucky number" via a declared {@code toolType: "http"} tool pointed at a public test API, and
     * must weave the real fetched number into its final answer.
     */
    @Test
    void httpToolCallingAgentActuallyInvokesRealHttpTool() {
        String agentName = "http_tool_agentspan_e2e_" + UUID.randomUUID();
        deployAgent(
                AgentConfig.builder()
                        .name(agentName)
                        .model(MODEL)
                        .instructions(
                                "You are a helpful assistant. Whenever the user asks for a random"
                                        + " or lucky number, call the get_random_number tool"
                                        + " EXACTLY ONCE to fetch one -- never make up a number"
                                        + " yourself. As soon as you have the tool result, respond"
                                        + " in one short sentence that includes the randomInt"
                                        + " value from the tool result.")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("get_random_number")
                                                .description(
                                                        "Fetches a random integer (field"
                                                                + " randomInt) from an external"
                                                                + " test API. Call at most once"
                                                                + " per request.")
                                                .toolType("http")
                                                .inputSchema(
                                                        Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(),
                                                                "required", List.of()))
                                                .config(
                                                        Map.of(
                                                                "url",
                                                                "https://orkes-api-tester.orkesconductor.com/api",
                                                                "method",
                                                                "GET"))
                                                .build()))
                        .maxTurns(3)
                        .build());

        String wfName = "call_agentspan_http_tool_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, "Give me a lucky number for today.");
        String workflowId = startWorkflow(wfName, new HashMap<>());

        Workflow completed = awaitTerminal(workflowId, 120);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task chat = agentTaskOf(completed);
        assertEquals("completed", chat.getOutputData().get("state"));
        String executionId = (String) chat.getOutputData().get("executionId");

        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        List<Task> httpCalls = tasksOfType(agentExecution, "HTTP");
        assertTrue(
                !httpCalls.isEmpty(),
                "the LLM must have actually decided to call the HTTP tool at least once");
        for (Task httpCall : httpCalls) {
            @SuppressWarnings("unchecked")
            Map<String, Object> response =
                    (Map<String, Object>) httpCall.getOutputData().get("response");
            assertEquals(200, response.get("statusCode"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.get("body");
            assertNotNull(body.get("randomInt"), "the real test API must return a randomInt");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chat.getOutputData().get("output");
        String resultText = String.valueOf(output.get("result"));
        assertTrue(
                Pattern.compile("\\d+").matcher(resultText).find(),
                "final answer should include the fetched number: " + resultText);
    }

    /**
     * Proves the full loop this feature exists for: a real, LLM-driven agent that (1) calls a slow
     * HTTP tool (a genuine multi-poll {@code AGENT} task, not a hardcoded {@code WAIT}), and (2)
     * pauses twice via a {@code toolType: "human"} tool, each time resumed through a second {@code
     * AGENT} call carrying the same {@code executionId} — exactly the {@code
     * ai/examples/32-conductor-agent-human-in-loop.json} idiom, driven by a {@code DO_WHILE(chat,
     * human)} caller loop the same shape as {@link
     * ConductorAgentEndToEndTest#conversationLoopAlternatesLongRunningAgentAndHumanUntilComplete}.
     *
     * <p>Note: for a real {@code toolType: "human"} pause, {@code
     * ConductorAgentDelegate.pendingTool()} does not surface the LLM's own question argument (only
     * {@code tool_name}/{@code parameters}/{@code response_schema}, which are null/generic here) —
     * so this driver reads the question directly off the child execution's paused {@code HUMAN}
     * task input, the same workaround used to explore this by hand.
     */
    @Test
    void combinedLongRunningToolCallingAndHumanInLoopConversation() {
        String agentName = "conversation_agentspan_e2e_" + UUID.randomUUID();
        deployAgent(
                AgentConfig.builder()
                        .name(agentName)
                        .model(MODEL)
                        .instructions(
                                "You are conducting a short interview, in this exact order:\n"
                                        + "1. Use the ask_question tool to ask the user for their"
                                        + " name.\n"
                                        + "2. Once you have their name, call get_random_number"
                                        + " EXACTLY ONCE to fetch a lucky number for them (never"
                                        + " call it more than once).\n"
                                        + "3. Then use ask_question again to ask for their"
                                        + " favorite color.\n"
                                        + "4. Once you have their favorite color, respond in ONE"
                                        + " short sentence that mentions their name, their lucky"
                                        + " number, and their favorite color. Do not call any"
                                        + " more tools after that.")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("get_random_number")
                                                .description(
                                                        "Fetches a random lucky number (field"
                                                                + " randomInt) from an external"
                                                                + " test API. Takes a few seconds."
                                                                + " Call at most once.")
                                                .toolType("http")
                                                .inputSchema(
                                                        Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(),
                                                                "required", List.of()))
                                                .config(
                                                        Map.of(
                                                                "url",
                                                                "https://orkes-api-tester.orkesconductor.com/api?sleepFor=3000",
                                                                "method",
                                                                "GET"))
                                                .build(),
                                        ToolConfig.builder()
                                                .name("ask_question")
                                                .description(
                                                        "Ask the human user a question and wait"
                                                                + " for their answer.")
                                                .toolType("human")
                                                .inputSchema(
                                                        Map.of(
                                                                "type",
                                                                "object",
                                                                "properties",
                                                                Map.of(
                                                                        "question",
                                                                        Map.of(
                                                                                "type",
                                                                                "string",
                                                                                "description",
                                                                                "The"
                                                                                        + " question"
                                                                                        + " to"
                                                                                        + " ask"
                                                                                        + " the"
                                                                                        + " user")),
                                                                "required",
                                                                List.of("question")))
                                                .build()))
                        .maxTurns(8)
                        .build());

        String wfName = "conductor_agent_conversation_agentspan_e2e_" + UUID.randomUUID();
        registerConversationLoopWorkflow(wfName, agentName);
        String workflowId = startWorkflow(wfName, Map.of("initialPrompt", "Hi, I'd like to chat."));

        Workflow completed = awaitConversationTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        List<Task> chatCalls = tasksOfType(completed, "AGENT");
        List<Task> humanTurns = tasksOfType(completed, "HUMAN");
        assertTrue(
                chatCalls.size() >= 2,
                "at least a fresh start + one resume across the two questions; got "
                        + chatCalls.size());
        assertTrue(!humanTurns.isEmpty());

        Task lastChat = chatCalls.get(chatCalls.size() - 1);
        assertEquals("completed", lastChat.getOutputData().get("state"));
        String executionId = (String) lastChat.getOutputData().get("executionId");

        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        List<Task> httpCalls = tasksOfType(agentExecution, "HTTP");
        assertTrue(!httpCalls.isEmpty(), "the slow lucky-number tool must have actually run");
        List<Task> innerHumanCalls = tasksOfType(agentExecution, "HUMAN");
        assertTrue(
                innerHumanCalls.size() >= 2,
                "the agent must have asked at least the name and color questions; got "
                        + innerHumanCalls.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) lastChat.getOutputData().get("output");
        String resultText = String.valueOf(output.get("result"));
        assertTrue(
                Pattern.compile("\\d+").matcher(resultText).find(),
                "final synthesis should include the fetched lucky number: " + resultText);
    }

    // ── AgentSpan registration ─────────────────────────────────────────────

    private void deployAgent(AgentConfig config) {
        AgentStartResponse response =
                agentService.deploy(AgentStartRequest.builder().agentConfig(config).build());
        assertEquals(config.getName(), response.getAgentName());
    }

    // ── engine helpers (parallel to ConductorAgentEndToEndTest's, but this class also drains
    // LLM_CHAT_COMPLETE/HTTP/JOIN — the async task set a real reasoning-loop agent compiles to) ──

    private Workflow awaitTerminal(String workflowId) {
        return awaitTerminal(workflowId, 60);
    }

    private Workflow awaitTerminal(String workflowId, int timeoutSeconds) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAllQueues();
                            // AGENT is a synchronous system task, driven by the decider rather than
                            // the async queues drainAllQueues() pops — re-decide to re-invoke its
                            // execute() (poll) each cycle.
                            workflowExecutor.decide(workflowId);
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            return wf != null
                                    && wf.getStatus() != null
                                    && wf.getStatus().isTerminal();
                        });
        return latest.get();
    }

    private Workflow awaitConversationTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(180, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAllQueues();
                            // AGENT is synchronous — re-decide to drive its execute() poll.
                            workflowExecutor.decide(workflowId);
                            answerPendingHumanTask(workflowId);
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            return wf != null
                                    && wf.getStatus() != null
                                    && wf.getStatus().isTerminal();
                        });
        return latest.get();
    }

    /**
     * Drains every registered async system-task queue (LLM_CHAT_COMPLETE, HTTP, JOIN, ...). The
     * AGENT task is synchronous and is driven by {@code workflowExecutor.decide(workflowId)} in the
     * await loops instead.
     */
    private void drainAllQueues() {
        for (WorkflowSystemTask task : asyncSystemTasks) {
            for (String taskId : queueDAO.pop(task.getTaskType(), 5, 100)) {
                asyncSystemTaskExecutor.execute(task, taskId);
            }
        }
    }

    /**
     * Stands in for "the human". A real {@code toolType: "human"} pause doesn't surface its
     * question via the AGENT task's {@code pendingTool} (see class javadoc) — reads it directly off
     * the child execution's paused HUMAN task input instead.
     */
    private void answerPendingHumanTask(String workflowId) {
        Workflow workflow = executionService.getExecutionStatus(workflowId, true);
        Task human = lastTaskOfType(workflow, "HUMAN");
        if (human == null || human.getStatus() != Task.Status.IN_PROGRESS) {
            return;
        }

        String answer = "ok, thanks!";
        Task chat = lastTaskOfType(workflow, "AGENT");
        String question = chat != null ? pendingQuestion(chat) : "";
        if (question.toLowerCase().contains("name")) {
            answer = "Alice";
        } else if (question.toLowerCase().contains("color")) {
            answer = "blue";
        }

        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(human.getTaskId());
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(Map.of("answer", answer));
        workflowExecutor.updateTask(taskResult);
    }

    private String pendingQuestion(Task chat) {
        String executionId = (String) chat.getOutputData().get("executionId");
        if (executionId == null) {
            return "";
        }
        Workflow child = executionService.getExecutionStatus(executionId, true);
        Task pendingHuman = lastTaskOfType(child, "HUMAN");
        if (pendingHuman == null || pendingHuman.getStatus() != Task.Status.IN_PROGRESS) {
            return "";
        }
        Object question = pendingHuman.getInputData().get("question");
        return question != null ? question.toString() : "";
    }

    private Task lastTaskOfType(Workflow workflow, String taskType) {
        List<Task> matches = tasksOfType(workflow, taskType);
        return matches.isEmpty() ? null : matches.get(matches.size() - 1);
    }

    private List<Task> tasksOfType(Workflow workflow, String taskType) {
        if (workflow == null || workflow.getTasks() == null) {
            return List.of();
        }
        return workflow.getTasks().stream()
                .filter(t -> taskType.equals(t.getTaskType()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Task agentTaskOf(Workflow workflow) {
        return workflow.getTasks().stream()
                .filter(t -> "AGENT".equals(t.getTaskType()))
                .findFirst()
                .orElse(null);
    }

    private String startWorkflow(String name, Map<String, Object> input) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(name);
        swi.setVersion(1);
        swi.setWorkflowInput(input);
        return workflowExecutor.startWorkflow(swi);
    }

    // ── caller-workflow registration ────────────────────────────────────────

    private void registerCallAgentWorkflow(String name, String agentName, String text) {
        ensureTaskDef("AGENT");

        WorkflowTask task = new WorkflowTask();
        task.setName("AGENT");
        task.setTaskReferenceName("chat");
        task.setType("AGENT");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("agentType", "conductor");
        taskInput.put("name", agentName);
        taskInput.put("prompt", text);
        taskInput.put("pollIntervalSeconds", 1);
        taskInput.put("maxDurationSeconds", 120);
        task.setInputParameters(taskInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("agentspan-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /** Same {@code DO_WHILE(chat, human)} shape as {@code ConductorAgentEndToEndTest}. */
    private void registerConversationLoopWorkflow(String name, String agentName) {
        ensureTaskDef("AGENT");
        ensureTaskDef("HUMAN");
        ensureTaskDef("INLINE");

        // No more message>parts>text>prompt fallback (ConductorAgentRequest has a single `prompt`
        // field) — resolve the effective prompt explicitly before `chat` runs, same fix as
        // ConductorAgentEndToEndTest#registerConversationLoopWorkflow.
        WorkflowTask resolvePrompt = new WorkflowTask();
        resolvePrompt.setName("INLINE");
        resolvePrompt.setTaskReferenceName("resolve_prompt");
        resolvePrompt.setType("INLINE");
        Map<String, Object> resolveInput = new HashMap<>();
        resolveInput.put("initialPrompt", "${workflow.input.initialPrompt}");
        resolveInput.put("humanAnswer", "${human.output.answer}");
        resolveInput.put("evaluatorType", "javascript");
        resolveInput.put(
                "expression", "({prompt: $.humanAnswer ? $.humanAnswer : $.initialPrompt})");
        resolvePrompt.setInputParameters(resolveInput);

        WorkflowTask chat = new WorkflowTask();
        chat.setName("AGENT");
        chat.setTaskReferenceName("chat");
        chat.setType("AGENT");
        Map<String, Object> chatInput = new HashMap<>();
        chatInput.put("agentType", "conductor");
        chatInput.put("name", agentName);
        chatInput.put("executionId", "${chat.output.executionId}");
        chatInput.put("prompt", "${resolve_prompt.output.result.prompt}");
        chatInput.put("pollIntervalSeconds", 1);
        chatInput.put("maxDurationSeconds", 180);
        chat.setInputParameters(chatInput);

        WorkflowTask human = new WorkflowTask();
        human.setName("HUMAN");
        human.setTaskReferenceName("human");
        human.setType("HUMAN");

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType("DO_WHILE");
        loop.setLoopCondition(
                "if ( $.chat['state'] == 'input-required' && $.loop['iteration'] < 6 ) {"
                        + " true; } else { false; }");
        loop.setLoopOver(List.of(resolvePrompt, chat, human));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("agentspan-e2e@conductor.test");
        def.setTasks(List.of(loop));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private void ensureTaskDef(String taskType) {
        TaskDef td = new TaskDef();
        td.setName(taskType);
        td.setRetryCount(0);
        td.setTimeoutSeconds(180);
        try {
            metadataService.registerTaskDef(List.of(td));
        } catch (Exception ignored) {
            // already registered by a prior test
        }
    }
}
