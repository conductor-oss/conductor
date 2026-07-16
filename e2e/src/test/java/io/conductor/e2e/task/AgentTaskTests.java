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
package io.conductor.e2e.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box, real-server coverage of the {@code AGENT} task's {@code conductor} branch — the same
 * scenarios proved out against an embedded Spring context + testcontainers-Redis in {@code
 * test-harness}'s {@code ConductorAgentEndToEndTest}, here driven purely over REST against the
 * fully docker-composed server (real persistence/search/queue, real decider, real background
 * task-worker coordinator — no manual queue draining needed).
 *
 * <p>Every agent registered here is a plain {@link WorkflowDef} stamped with {@code
 * metadata.agentDef} (the minimal contract {@code WorkflowDef.isAgent()} checks — see {@code
 * AgentSpanRegistrationEndToEndTest} in test-harness for the real {@code
 * AgentService.deploy()}-compiled equivalent, which needs an LLM and isn't ported here). All
 * scenarios are entirely native system tasks ({@code WAIT}/{@code INLINE}/{@code HUMAN}) — no
 * worker, no LLM, deterministic.
 *
 * <p>{@link #registerAgentTaskDef} globally registers {@code "AGENT"} with {@code retryCount=0} so
 * the failure-path tests below fail fast on a single attempt instead of accumulating the
 * class-default {@code retryCount=3}. An inline {@code TaskDef} via {@code WorkflowTask
 * .setTaskDefinition()} was tried first — it's only consulted by {@code DoWhileTaskMapper}'s own
 * sub-scheduling logic, and had no effect on this plain top-level {@code AGENT} task when tested
 * empirically (it kept retrying 4 times regardless), so a real global registration is required.
 */
@Disabled
class AgentTaskTests {

    private static final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
    private static final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private static final TaskClient taskClient = ApiUtil.TASK_CLIENT;

    @BeforeAll
    static void registerAgentTaskDef() {
        // An inline TaskDef via WorkflowTask.setTaskDefinition() is only consulted by the
        // DO_WHILE mapper's own sub-scheduling logic -- verified empirically here: setting it on
        // a plain top-level AGENT task had no effect, and the task kept retrying with the
        // class-default retryCount=3 regardless. A real global registration is required to get
        // fast, single-attempt failures for the failure-path tests below.
        TaskDef taskDef = new TaskDef("AGENT");
        taskDef.setRetryCount(0);
        try {
            metadataClient.registerTaskDefs(List.of(taskDef));
        } catch (Exception ignored) {
            // already registered by a prior run against this server
        }
    }

    @Test
    void helloWorldAgentCompletesAndExposesSubWorkflowId() {
        String agentName = "hello_world_agent_e2e_" + UUID.randomUUID();
        registerHelloWorldAgent(agentName);

        String wfName = "call_agent_hello_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

        Workflow completed = awaitTerminal(workflowId, 30);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals("COMPLETED", agentTask.getOutputData().get("state"));
        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertNotNull(executionId);
        assertEquals(executionId, agentTask.getSubWorkflowId());
        assertTrue(
                String.valueOf(agentTask.getOutputData().get("text"))
                        .contains("Hello, world! You said: are you there?"));

        Workflow agentExecution = workflowClient.getWorkflow(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
    }

    @Test
    void longRunningAgentPollsMultipleTimesBeforeCompleting() {
        String agentName = "long_running_agent_e2e_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String wfName = "call_agent_long_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

        Workflow completed = awaitTerminal(workflowId, 30);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals("COMPLETED", agentTask.getOutputData().get("state"));
        // The 3x(WAIT 2s)+INLINE child running to completion is itself proof the AGENT was polled
        // across multiple sweeps — a single immediate poll couldn't have completed a ~6s child.
        // (pollCount is not applicable: it is only incremented on the async system-task queue path,
        // which a synchronous AGENT no longer uses.)
        assertTrue(
                String.valueOf(agentTask.getOutputData().get("text"))
                        .contains("Long-running agent finished after 3 waits."));
    }

    @Test
    void conversationLoopAlternatesAgentAndHumanUntilComplete() {
        String agentName = "conversation_agent_e2e_" + UUID.randomUUID();
        registerConversationAgent(agentName);

        String wfName = "conversation_loop_e2e_" + UUID.randomUUID();
        registerConversationLoopWorkflow(wfName, agentName);
        String workflowId = startWorkflow(wfName, Map.of("initialPrompt", "Hi, I'd like to chat."));

        Workflow completed = awaitConversationTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        List<Task> chatCalls = tasksOfType(completed, "AGENT");
        List<Task> humanTurns = tasksOfType(completed, "HUMAN");
        assertEquals(3, chatCalls.size(), "fresh start + 2 resumes, one per question asked");
        assertEquals(3, humanTurns.size());

        Task turn1 = chatCalls.get(0);
        assertEquals("WAITING", turn1.getOutputData().get("state"));
        String executionId = (String) turn1.getOutputData().get("executionId");
        assertEquals(executionId, turn1.getSubWorkflowId());

        Task turn2 = chatCalls.get(1);
        assertEquals("WAITING", turn2.getOutputData().get("state"));
        assertEquals(
                executionId,
                turn2.getOutputData().get("executionId"),
                "resume must reattach to the SAME child execution, never start a new one");

        Task turn3 = chatCalls.get(2);
        assertEquals("COMPLETED", turn3.getOutputData().get("state"));
        assertTrue(
                String.valueOf(turn3.getOutputData().get("text"))
                        .contains("Thanks Alice! I will remember your favorite color is blue."));
    }

    @Test
    void terminatingParentCancelsInFlightConductorAgent() {
        String agentName = "conversation_agent_cancel_e2e_" + UUID.randomUUID();
        registerConversationAgent(agentName);

        String wfName = "conversation_cancel_e2e_" + UUID.randomUUID();
        registerConversationLoopWorkflow(wfName, agentName);
        String workflowId = startWorkflow(wfName, Map.of("initialPrompt", "Hi, I'd like to chat."));

        String[] executionIdHolder = new String[1];
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Task chat = agentTaskOf(workflowClient.getWorkflow(workflowId, true));
                            assertNotNull(chat);
                            assertEquals(Task.Status.IN_PROGRESS, chat.getStatus());
                            String executionId = (String) chat.getOutputData().get("executionId");
                            assertNotNull(executionId);
                            executionIdHolder[0] = executionId;
                        });

        String executionId = executionIdHolder[0];
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                workflowClient.getWorkflow(executionId, false).getStatus());

        workflowClient.terminateWorkflow(workflowId, "test: cancel mid-conversation");

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () ->
                                assertTrue(
                                        workflowClient
                                                .getWorkflow(executionId, false)
                                                .getStatus()
                                                .isTerminal()));

        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                workflowClient.getWorkflow(executionId, false).getStatus(),
                "cancel() must propagate termination to the in-flight conductor agent execution");
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                workflowClient.getWorkflow(workflowId, true).getStatus());
    }

    @Test
    void agentExceedingMaxDurationFailsTerminallyAndTerminatesTheChild() {
        String agentName = "slow_agent_e2e_" + UUID.randomUUID();
        registerSlowAgent(agentName, "10 seconds");

        String wfName = "call_agent_timeout_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, Map.of("maxDurationSeconds", 3));
        String workflowId = startWorkflow(wfName, Map.of());

        Workflow completed = awaitTerminal(workflowId, 30);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, agentTask.getStatus());
        assertTrue(
                agentTask.getReasonForIncompletion().contains("exceeded max duration of 3s"),
                "reason: " + agentTask.getReasonForIncompletion());

        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                workflowClient.getWorkflow(executionId, false).getStatus(),
                "the deadline guard must terminate the child, not leave it orphaned/running");
    }

    /**
     * The agent <b>itself</b> timing out — distinct from every other timeout scenario in this
     * class:
     *
     * <ul>
     *   <li>{@code agentExceedingMaxDurationFailsTerminallyWithoutCancelingTheChild} (above) is the
     *       AGENT <i>wrapper</i> task's own {@code maxDurationSeconds} deadline, enforced by {@code
     *       ConductorAgentDelegate.execute()} while the child is merely {@code RUNNING}.
     *   <li>A per-task {@code TaskDef.responseTimeoutSeconds} (tried and abandoned — see the class
     *       javadoc) never fires for a system task like AGENT/WAIT — that mechanism assumes an
     *       external worker polling and going silent, which doesn't apply here.
     * </ul>
     *
     * <p>Here the registered agent's <b>own</b> {@code WorkflowDef.timeoutSeconds} + {@code
     * timeoutPolicy=TIME_OUT_WF} fires instead — a completely different, task-type-agnostic
     * mechanism ({@code DeciderService.checkWorkflowTimeout}, purely wall-clock: {@code workflow
     * .getCreateTime()} vs. {@code workflowDef.getTimeoutSeconds()}, checked unconditionally at the
     * top of every {@code decide()} cycle). The child's own workflow ends {@code TIMED_OUT}; {@code
     * ConductorAgentDelegate.deriveState} explicitly groups {@code TIMED_OUT} with {@code FAILED},
     * so the wrapper AGENT task lands on plain {@code FAILED} (not {@code
     * FAILED_WITH_TERMINAL_ERROR}) with the child's own timeout message.
     */
    @Test
    void agentsOwnWorkflowTimeoutMapsToFailedOnTheAgentTask() {
        String agentName = "self_timeout_agent_e2e_" + UUID.randomUUID();
        registerSlowAgentWithOwnTimeout(agentName, "10 seconds", 3);

        String wfName = "call_agent_selftimeout_e2e_" + UUID.randomUUID();
        // Generous maxDurationSeconds so the WRAPPER's own deadline guard can't race with (or
        // mask) the CHILD's much shorter self-timeout.
        registerCallAgentWorkflow(wfName, agentName, Map.of("maxDurationSeconds", 60));
        String workflowId = startWorkflow(wfName, Map.of());

        Workflow completed = awaitTerminal(workflowId, 90);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertEquals("FAILED", agentTask.getOutputData().get("state"));
        assertTrue(
                agentTask.getReasonForIncompletion().contains("Workflow timed out after"),
                "reason: " + agentTask.getReasonForIncompletion());

        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertEquals(
                Workflow.WorkflowStatus.TIMED_OUT,
                workflowClient.getWorkflow(executionId, false).getStatus());
    }

    @Test
    void agentInternalFailureMapsToFailedOnTheAgentTask() {
        String agentName = "failing_agent_e2e_" + UUID.randomUUID();
        registerFailingAgent(agentName);

        String wfName = "call_agent_failure_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

        // 60s, not 30s: registerAgentTaskDef's retryCount=0 makes this fail on the first attempt
        // once the server's metadata cache picks it up, but tolerates the worst case (the
        // class-default retryCount=3, ~45-50s observed) if that registration hasn't propagated
        // yet against a long-lived server.
        Workflow completed = awaitTerminal(workflowId, 60);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertNotNull(agentTask.getReasonForIncompletion());
    }

    @Test
    void referencingUnregisteredAgentFailsWithNotFound() {
        String bogusAgentName = "does_not_exist_" + UUID.randomUUID();
        String wfName = "call_agent_missing_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, bogusAgentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

        Workflow completed = awaitTerminal(workflowId, 60);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertTrue(
                agentTask.getReasonForIncompletion().contains("Agent not found"),
                "reason: " + agentTask.getReasonForIncompletion());
    }

    @Test
    void childCanceledIndependentlySurfacesAsCanceledOnNextPoll() {
        String agentName = "long_running_agent_extcancel_e2e_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String wfName = "call_agent_extcancel_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

        String[] executionIdHolder = new String[1];
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Task chat = agentTaskOf(workflowClient.getWorkflow(workflowId, true));
                            String executionId =
                                    chat != null
                                            ? (String) chat.getOutputData().get("executionId")
                                            : null;
                            assertNotNull(executionId);
                            executionIdHolder[0] = executionId;
                        });

        workflowClient.terminateWorkflow(
                executionIdHolder[0], "operator canceled the agent execution directly");

        Workflow completed = awaitTerminal(workflowId, 30);
        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.CANCELED, agentTask.getStatus());
        assertEquals("CANCELED", agentTask.getOutputData().get("state"));
        assertEquals(
                "operator canceled the agent execution directly",
                agentTask.getReasonForIncompletion());
    }

    /**
     * Two concurrent {@code AGENT} tasks (a {@code FORK_JOIN}) call the SAME registered agent at
     * the same time with different prompts. Proves the two runs stay fully independent — distinct
     * {@code executionId}s, no cross-talk in the deterministic idempotency key ({@code
     * workflowInstanceId + referenceTaskName + iteration}, which differs here on {@code
     * referenceTaskName} between the two fork branches) — rather than accidentally colliding on a
     * shared child execution.
     *
     * <p>Two scenarios explored and deliberately <b>not</b> included here, because they turned out
     * not to model real behavior for a system task like AGENT (driven internally by {@code
     * AsyncSystemTaskExecutor}, not polled by an external worker): engine-level {@code
     * TaskDef.responseTimeoutSeconds} never fired even set far shorter than the agent's own
     * completion time (the workflow simply ran to normal completion), and {@code
     * TaskDef.retryDelaySeconds} was not honored between automatic retries (three attempts
     * completed within ~5 seconds despite a configured 2s delay) — so a deterministic "fails once,
     * recovers on retry" test isn't achievable without a real timing race.
     */
    @Test
    void concurrentCallsToTheSameAgentStayIndependent() {
        String agentName = "hello_world_agent_concurrent_e2e_" + UUID.randomUUID();
        registerHelloWorldAgent(agentName);

        WorkflowTask branch1 = callAgentTask(agentName, Map.of("prompt", "I am branch one."));
        branch1.setTaskReferenceName("chat1");
        WorkflowTask branch2 = callAgentTask(agentName, Map.of("prompt", "I am branch two."));
        branch2.setTaskReferenceName("chat2");

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType("FORK_JOIN");
        fork.setForkTasks(List.of(List.of(branch1), List.of(branch2)));

        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join");
        join.setType("JOIN");
        join.setJoinOn(List.of("chat1", "chat2"));

        String wfName = "call_agent_concurrent_e2e_" + UUID.randomUUID();
        WorkflowDef def = new WorkflowDef();
        def.setName(wfName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(fork, join));
        metadataClient.updateWorkflowDefs(List.of(def));

        String workflowId = startWorkflow(wfName, Map.of());
        Workflow completed = awaitTerminal(workflowId, 30);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task chat1 =
                completed.getTasks().stream()
                        .filter(t -> "chat1".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();
        Task chat2 =
                completed.getTasks().stream()
                        .filter(t -> "chat2".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();

        String executionId1 = (String) chat1.getOutputData().get("executionId");
        String executionId2 = (String) chat2.getOutputData().get("executionId");
        assertNotNull(executionId1);
        assertNotNull(executionId2);
        assertNotEquals(
                executionId1, executionId2, "each fork branch must get its own child execution");
        assertTrue(String.valueOf(chat1.getOutputData().get("text")).contains("branch one"));
        assertTrue(String.valueOf(chat2.getOutputData().get("text")).contains("branch two"));
    }

    // ── engine helpers ─────────────────────────────────────────────────────

    private static Workflow awaitTerminal(String workflowId, int timeoutSeconds) {
        Workflow[] latest = new Workflow[1];
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            latest[0] = wf;
                            assertNotNull(wf.getStatus());
                            assertTrue(wf.getStatus().isTerminal());
                        });
        return latest[0];
    }

    /** Also plays "the human" for the {@code DO_WHILE(chat, human)} conversation workflows. */
    private static Workflow awaitConversationTerminal(String workflowId) {
        Workflow[] latest = new Workflow[1];
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            answerPendingHumanTask(workflowId);
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            latest[0] = wf;
                            assertNotNull(wf.getStatus());
                            assertTrue(wf.getStatus().isTerminal());
                        });
        return latest[0];
    }

    private static void answerPendingHumanTask(String workflowId) {
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task human = lastTaskOfType(workflow, "HUMAN");
        if (human == null || human.getStatus() != Task.Status.IN_PROGRESS) {
            return;
        }

        String answer = "ok, thanks!";
        Task chat = lastTaskOfType(workflow, "AGENT");
        if (chat != null
                && chat.getOutputData().get("pendingTool") instanceof Map<?, ?> pending
                && pending.get("parameters") instanceof Map<?, ?> parameters) {
            String question = String.valueOf(parameters.get("question"));
            if (question.contains("name")) {
                answer = "Alice";
            } else if (question.contains("favorite color")) {
                answer = "blue";
            }
        }

        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(human.getTaskId());
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(Map.of("answer", answer));
        taskClient.updateTask(taskResult);
    }

    private static Task lastTaskOfType(Workflow workflow, String taskType) {
        List<Task> matches = tasksOfType(workflow, taskType);
        return matches.isEmpty() ? null : matches.get(matches.size() - 1);
    }

    private static List<Task> tasksOfType(Workflow workflow, String taskType) {
        if (workflow == null || workflow.getTasks() == null) {
            return List.of();
        }
        List<Task> matches = new ArrayList<>();
        for (Task t : workflow.getTasks()) {
            if (taskType.equals(t.getTaskType())) {
                matches.add(t);
            }
        }
        return matches;
    }

    private static Task agentTaskOf(Workflow workflow) {
        return lastTaskOfType(workflow, "AGENT");
    }

    private static String startWorkflow(String name, Map<String, Object> input) {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(name);
        request.setVersion(1);
        request.setInput(input);
        return workflowClient.startWorkflow(request);
    }

    // ── registration ────────────────────────────────────────────────────────

    private static void registerHelloWorldAgent(String agentName) {
        WorkflowTask hello = new WorkflowTask();
        hello.setName("INLINE");
        hello.setTaskReferenceName("hello");
        hello.setType("INLINE");
        Map<String, Object> helloInput = new HashMap<>();
        helloInput.put("input", "${workflow.input}");
        helloInput.put("evaluatorType", "javascript");
        helloInput.put("expression", "({text: 'Hello, world! You said: ' + $.input.prompt})");
        hello.setInputParameters(helloInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(hello));
        def.setOutputParameters(Map.of("text", "${hello.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerSlowAgent(String agentName, String waitDuration) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("WAIT");
        wait.setTaskReferenceName("wait");
        wait.setType("WAIT");
        wait.setInputParameters(Map.of("duration", waitDuration));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(wait));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * A {@code WAIT(waitDuration)} agent slower than {@code selfTimeoutSeconds}, with its own
     * {@code WorkflowDef.timeoutSeconds}/{@code TIME_OUT_WF} set so ITS workflow (not any AGENT
     * task wrapping it) times out on its own.
     */
    private static void registerSlowAgentWithOwnTimeout(
            String agentName, String waitDuration, int selfTimeoutSeconds) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("WAIT");
        wait.setTaskReferenceName("wait");
        wait.setType("WAIT");
        wait.setInputParameters(Map.of("duration", waitDuration));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(wait));
        def.setTimeoutSeconds(selfTimeoutSeconds);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerFailingAgent(String agentName) {
        WorkflowTask boom = new WorkflowTask();
        boom.setName("INLINE");
        boom.setTaskReferenceName("boom");
        boom.setType("INLINE");
        boom.setInputParameters(Map.of("evaluatorType", "not_a_real_evaluator", "expression", "1"));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(boom));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerLongRunningAgent(String agentName) {
        List<WorkflowTask> tasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            WorkflowTask wait = new WorkflowTask();
            wait.setName("WAIT");
            wait.setTaskReferenceName("wait" + i);
            wait.setType("WAIT");
            wait.setInputParameters(Map.of("duration", "2 seconds"));
            tasks.add(wait);

            WorkflowTask step = new WorkflowTask();
            step.setName("INLINE");
            step.setTaskReferenceName("step" + i);
            step.setType("INLINE");
            Map<String, Object> stepInput = new HashMap<>();
            stepInput.put("input", "${workflow.input}");
            stepInput.put("evaluatorType", "javascript");
            stepInput.put(
                    "expression",
                    i < 3
                            ? "({note: 'step" + i + " done'})"
                            : "({text: 'Long-running agent finished after 3 waits. You said: '"
                                    + " + $.input.prompt})");
            step.setInputParameters(stepInput);
            tasks.add(step);
        }

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(tasks);
        def.setOutputParameters(Map.of("text", "${step3.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerConversationAgent(String agentName) {
        WorkflowTask wait1 = waitTask("wait1");
        WorkflowTask greet = inlineTask("greet", Map.of("prompt", "${workflow.input.prompt}"));
        greet.getInputParameters()
                .put(
                        "expression",
                        "({text: 'Hello! You said: ' + $.prompt + '. What is your name?'})");

        WorkflowTask askName = humanQuestionTask("ask_name", "${greet.output.result.text}");

        WorkflowTask wait2 = waitTask("wait2");
        WorkflowTask followup = inlineTask("followup", Map.of("name", "${ask_name.output.result}"));
        followup.getInputParameters()
                .put(
                        "expression",
                        "({text: 'Nice to meet you, ' + $.name + '! What is your favorite color?'})");

        WorkflowTask askColor = humanQuestionTask("ask_color", "${followup.output.result.text}");

        WorkflowTask wait3 = waitTask("wait3");
        Map<String, Object> finalizeInput = new HashMap<>();
        finalizeInput.put("name", "${ask_name.output.result}");
        finalizeInput.put("color", "${ask_color.output.result}");
        WorkflowTask finalize = inlineTask("finalize", finalizeInput);
        finalize.getInputParameters()
                .put(
                        "expression",
                        "({text: 'Thanks ' + $.name + '! I will remember your favorite color is '"
                                + " + $.color + '. Conversation complete.'})");

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(wait1, greet, askName, wait2, followup, askColor, wait3, finalize));
        def.setOutputParameters(Map.of("text", "${finalize.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerConversationLoopWorkflow(String name, String agentName) {
        // No more message>parts>text>prompt fallback (ConductorAgentRequest has a single
        // `prompt` field) — resolve the effective prompt explicitly before `chat` runs, same fix
        // as ConductorAgentEndToEndTest#registerConversationLoopWorkflow (test-harness module).
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
                "if ( $.chat['state'] == 'WAITING' && $.loop['iteration'] < 6 ) {"
                        + " true; } else { false; }");
        loop.setLoopOver(List.of(resolvePrompt, chat, human));

        registerWorkflow(name, loop);
    }

    private static WorkflowTask callAgentTask(String agentName, Map<String, Object> extraInput) {
        WorkflowTask task = new WorkflowTask();
        task.setName("AGENT");
        task.setTaskReferenceName("callAgent");
        task.setType("AGENT");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("agentType", "conductor");
        taskInput.put("name", agentName);
        taskInput.put("prompt", "are you there?");
        taskInput.put("pollIntervalSeconds", 1);
        if (extraInput != null) {
            taskInput.putAll(extraInput);
        }
        task.setInputParameters(taskInput);
        return task;
    }

    private static void registerCallAgentWorkflow(
            String name, String agentName, Map<String, Object> extraInput) {
        registerWorkflow(name, callAgentTask(agentName, extraInput));
    }

    private static void registerWorkflow(String name, WorkflowTask onlyTask) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(onlyTask));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static WorkflowTask waitTask(String refName) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("WAIT");
        wait.setTaskReferenceName(refName);
        wait.setType("WAIT");
        wait.setInputParameters(Map.of("duration", "2 seconds"));
        return wait;
    }

    private static WorkflowTask inlineTask(String refName, Map<String, Object> extraInput) {
        WorkflowTask task = new WorkflowTask();
        task.setName("INLINE");
        task.setTaskReferenceName(refName);
        task.setType("INLINE");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("evaluatorType", "javascript");
        taskInput.putAll(extraInput);
        task.setInputParameters(taskInput);
        return task;
    }

    private static WorkflowTask humanQuestionTask(String refName, String questionExpression) {
        WorkflowTask task = new WorkflowTask();
        task.setName("HUMAN");
        task.setTaskReferenceName(refName);
        task.setType("HUMAN");
        task.setInputParameters(
                Map.of(
                        "tool_name",
                        "ask_question",
                        "parameters",
                        Map.of("question", questionExpression)));
        return task;
    }
}
