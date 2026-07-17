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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
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
import io.orkes.conductor.client.AgentClient;
import io.orkes.conductor.client.model.agent.AgentRequest;
import io.orkes.conductor.client.model.agent.AgentStatusResponse;
import io.orkes.conductor.client.model.agent.RespondBody;
import io.orkes.conductor.client.model.agent.StartResponse;

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
 * <p>Every agent is deployed through Java SDK {@link AgentClient#deployAgent(AgentRequest)}. That
 * calls {@code AgentController /api/agent/deploy}, exercises normalization and compilation, stamps
 * the full agent definition, registers its workflow and tool task definitions, and returns the
 * server's registration response. No test hand-writes or stamps an agent {@link WorkflowDef}.
 *
 * <p>{@link #registerAgentTaskDef} registers {@code "AGENT"} with {@code retryCount=0} on a fresh
 * server so failure-path tests fail fast. If a shared server already owns that task definition, the
 * suite preserves it and allows enough time for its configured retries.
 */
class AgentTaskTests {

    private static final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
    private static final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private static final TaskClient taskClient = ApiUtil.TASK_CLIENT;
    private static final AgentClient agentClient = ApiUtil.AGENT_CLIENT;
    private static final String MODEL =
            System.getenv().getOrDefault("AGENT_E2E_MODEL", "openai/gpt-4o-mini");

    @BeforeAll
    static void registerAgentTaskDef() {
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
        assertEquals("completed", agentTask.getOutputData().get("state"));
        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertNotNull(executionId);
        assertEquals(executionId, agentTask.getSubWorkflowId());
        assertTrue(!agentResultText(agentTask).isBlank(), "real agent result must not be blank");

        Workflow agentExecution = workflowClient.getWorkflow(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        assertControllerDeployedAgent(agentName);
    }

    @Test
    void longRunningAgentPollsMultipleTimesBeforeCompleting() {
        String agentName = "long_running_agent_e2e_" + UUID.randomUUID();
        String workerTaskName = registerLongRunningAgent(agentName);

        String wfName = "call_agent_long_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(
                wfName, agentName, Map.of("pollIntervalSeconds", 5, "maxDurationSeconds", 60));
        String workflowId = startWorkflow(wfName, Map.of());

        Task firstPoll = awaitAgentPoll(workflowId, 1, 20);
        assertEquals(5, firstPoll.getCallbackAfterSeconds());
        int firstPollCount = firstPoll.getPollCount();
        long firstUpdateTime = firstPoll.getUpdateTime();

        Task secondPoll = awaitAgentPoll(workflowId, firstPollCount + 1, 15);
        long callbackDelayMillis = secondPoll.getUpdateTime() - firstUpdateTime;
        assertTrue(
                callbackDelayMillis >= 4_500,
                "AGENT was re-polled before its five-second callback: "
                        + callbackDelayMillis
                        + "ms");
        assertTrue(
                callbackDelayMillis < 8_000,
                "AGENT callback was delayed well beyond five seconds: "
                        + callbackDelayMillis
                        + "ms");

        completeWorkerTask(workerTaskName, "Long-running agent completed after two durable polls.");

        Workflow completed = awaitTerminal(workflowId, 20);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals("completed", agentTask.getOutputData().get("state"));
        assertTrue(agentTask.getPollCount() > secondPoll.getPollCount());
        assertTrue(!agentResultText(agentTask).isBlank(), "real agent result must not be blank");
    }

    @Test
    void agentClientStartsWaitsRespondsAndCompletesARealAgent() {
        String agentName = "approval_agent_e2e_" + UUID.randomUUID();
        String toolName = agentName + "_work";
        Map<String, Object> config = approvalAgentConfig(agentName, toolName);
        deployAgent(config);

        StartResponse started =
                agentClient.startAgent(
                        AgentRequest.nativeAgent(config)
                                .prompt("Use the required work tool, then summarize its result.")
                                .build());
        assertNotNull(started.getExecutionId());
        assertEquals(agentName, started.getAgentName());

        AgentStatusResponse waiting = awaitAgentStatus(started.getExecutionId(), true, false, 90);
        assertTrue(waiting.isWaiting());
        assertNotNull(waiting.getPendingTool());

        agentClient.respond(started.getExecutionId(), RespondBody.approve("approved by E2E"));
        completeWorkerTask(toolName, "approved tool result");

        AgentStatusResponse completed = awaitAgentStatus(started.getExecutionId(), false, true, 90);
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getOutput());
    }

    @Test
    void workflowWithTwoAgentTasksCarriesAConversationAcrossDurableRounds() {
        String firstAgent = "research_agent_e2e_" + UUID.randomUUID();
        String secondAgent = "review_agent_e2e_" + UUID.randomUUID();
        deployAgent(
                basicAgentConfig(
                        firstAgent,
                        "You are the first participant in a technical discussion. Respond concisely to the other participant's latest message."));
        deployAgent(
                basicAgentConfig(
                        secondAgent,
                        "You are the second participant in a technical discussion. Challenge or improve the other participant's latest message."));

        String wfName = "two_agent_chat_e2e_" + UUID.randomUUID();
        registerTwoAgentConversationWorkflow(wfName, firstAgent, secondAgent, 2);
        String workflowId =
                startWorkflow(
                        wfName,
                        Map.of(
                                "initialMessage",
                                "Durable execution is essential for reliable AI agents. What do you think?"));

        Workflow completed = awaitTerminal(workflowId, 120);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task conversationLoop = lastTaskOfType(completed, "DO_WHILE");
        assertNotNull(conversationLoop, "round-robin agent must compile to a durable loop");
        assertEquals(Task.Status.COMPLETED, conversationLoop.getStatus());
        assertEquals(2, ((Number) conversationLoop.getOutputData().get("iteration")).intValue());

        List<Task> agentTurns = tasksOfType(completed, "AGENT");
        assertEquals(4, agentTurns.size(), "two AGENT tasks must each execute for two rounds");
        assertTrue(
                agentTurns.stream().allMatch(t -> t.getStatus() == Task.Status.COMPLETED),
                "every agent turn must complete");

        List<Task> firstAgentTurns = agentTurns(completed, "agent_a");
        List<Task> secondAgentTurns = agentTurns(completed, "agent_b");
        assertEquals(2, firstAgentTurns.size(), "first AGENT task must speak twice");
        assertEquals(2, secondAgentTurns.size(), "second AGENT task must speak twice");

        String firstReply = agentResultText(firstAgentTurns.get(0));
        String secondReply = agentResultText(secondAgentTurns.get(0));
        assertTrue(!firstReply.isBlank());
        assertTrue(!secondReply.isBlank());
        assertTrue(
                String.valueOf(secondAgentTurns.get(0).getInputData().get("prompt"))
                        .contains(firstReply),
                "Agent B's first turn must receive Agent A's first reply");
        assertTrue(
                String.valueOf(firstAgentTurns.get(1).getInputData().get("prompt"))
                        .contains(secondReply),
                "Agent A's second turn must receive Agent B's first reply");

        assertControllerDeployedAgent(firstAgent);
        assertControllerDeployedAgent(secondAgent);
    }

    @Test
    void terminatingParentCancelsInFlightConductorAgent() {
        String agentName = "blocking_agent_cancel_e2e_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String wfName = "agent_cancel_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, null);
        String workflowId = startWorkflow(wfName, Map.of());

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
        registerSlowAgent(agentName);

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
     *   <li>{@code agentExceedingMaxDurationFailsTerminallyAndTerminatesTheChild} is the AGENT
     *       <i>wrapper</i> task's own {@code maxDurationSeconds} deadline.
     *   <li>This test configures {@code timeoutSeconds}/{@code TIME_OUT_WF} on the child agent
     *       workflow itself.
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
        registerSlowAgentWithOwnTimeout(agentName, 3);

        String wfName = "call_agent_selftimeout_e2e_" + UUID.randomUUID();
        // Generous maxDurationSeconds so the WRAPPER's own deadline guard can't race with (or
        // mask) the CHILD's much shorter self-timeout.
        registerCallAgentWorkflow(wfName, agentName, Map.of("maxDurationSeconds", 60));
        String workflowId = startWorkflow(wfName, Map.of());

        Task runningAgent = awaitAgentPoll(workflowId, 1, 20);
        String executionId = (String) runningAgent.getOutputData().get("executionId");
        assertNotNull(executionId);

        // A blocked SIMPLE task produces no child-workflow events by itself. Trigger the public
        // decider endpoint after the configured deadline so this test verifies the workflow's own
        // timeout deterministically instead of depending on the server sweeper cadence.
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () ->
                                System.currentTimeMillis()
                                                - workflowClient
                                                        .getWorkflow(executionId, false)
                                                        .getCreateTime()
                                        > 3_500);
        workflowClient.runDecider(executionId);

        // A shared server may have retries configured on the global AGENT task definition. The
        // child is already TIMED_OUT; allow those wrapper retries to settle before asserting the
        // final mapping.
        Workflow completed = awaitTerminal(workflowId, 45);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertEquals("failed", agentTask.getOutputData().get("state"));
        assertTrue(
                agentTask.getReasonForIncompletion().contains("Workflow timed out after"),
                "reason: " + agentTask.getReasonForIncompletion());

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
        assertEquals("canceled", agentTask.getOutputData().get("state"));
        assertEquals(
                "operator canceled the agent execution directly",
                agentTask.getReasonForIncompletion());
    }

    @Test
    void cancelAgentTaskStopsRunningConductorAgent() {
        String agentName = "long_running_agent_taskcancel_e2e_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String callerWorkflowName = "call_agent_taskcancel_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(callerWorkflowName, agentName, null);
        String callerWorkflowId = startWorkflow(callerWorkflowName, Map.of());

        Task runningAgent = awaitAgentPoll(callerWorkflowId, 1, 20);
        String executionId = (String) runningAgent.getOutputData().get("executionId");
        assertNotNull(executionId);

        String cancelWorkflowName = "cancel_agent_e2e_" + UUID.randomUUID();
        registerCancelAgentWorkflow(cancelWorkflowName);
        String cancelWorkflowId =
                startWorkflow(
                        cancelWorkflowName,
                        Map.of(
                                "executionId",
                                executionId,
                                "reason",
                                "canceled by the CANCEL_AGENT worker"));

        Workflow cancelWorkflow = awaitTerminal(cancelWorkflowId, 20);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, cancelWorkflow.getStatus());
        Task cancelTask = lastTaskOfType(cancelWorkflow, "CANCEL_AGENT");
        assertNotNull(cancelTask);
        assertEquals(true, cancelTask.getOutputData().get("canceled"));
        assertEquals(executionId, cancelTask.getOutputData().get("executionId"));

        Workflow callerWorkflow = awaitTerminal(callerWorkflowId, 20);
        Task canceledAgent = agentTaskOf(callerWorkflow);
        assertEquals(Task.Status.CANCELED, canceledAgent.getStatus());
        assertEquals("canceled", canceledAgent.getOutputData().get("state"));
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                workflowClient.getWorkflow(executionId, false).getStatus());
    }

    /**
     * Two concurrent {@code AGENT} tasks (a {@code FORK_JOIN}) call the SAME registered agent at
     * the same time with different prompts. Proves the two runs stay fully independent — distinct
     * {@code executionId}s, no cross-talk in the deterministic idempotency key ({@code
     * workflowInstanceId + referenceTaskName + iteration}, which differs here on {@code
     * referenceTaskName} between the two fork branches) — rather than accidentally colliding on a
     * shared child execution.
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
        assertTrue(!agentResultText(chat1).isBlank());
        assertTrue(!agentResultText(chat2).isBlank());
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

    private static Task awaitAgentPoll(
            String workflowId, int minimumPollCount, int timeoutSeconds) {
        Task[] latest = new Task[1];
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Task task = agentTaskOf(workflowClient.getWorkflow(workflowId, true));
                            assertNotNull(task);
                            latest[0] = task;
                            assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
                            assertTrue(
                                    task.getPollCount() >= minimumPollCount,
                                    "pollCount="
                                            + task.getPollCount()
                                            + ", expected at least "
                                            + minimumPollCount);
                        });
        return latest[0];
    }

    private static void completeWorkerTask(String taskType, String text) {
        Task[] polled = new Task[1];
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Task task = taskClient.pollTask(taskType, "agent-e2e-worker", null);
                            assertNotNull(task);
                            assertNotNull(task.getTaskId());
                            polled[0] = task;
                        });

        TaskResult result = new TaskResult(polled[0]);
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(Map.of("text", text));
        taskClient.updateTask(result);
    }

    private static AgentStatusResponse awaitAgentStatus(
            String executionId,
            boolean expectedWaiting,
            boolean expectedComplete,
            int timeoutSeconds) {
        AgentStatusResponse[] latest = new AgentStatusResponse[1];
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            AgentStatusResponse status = agentClient.getAgentStatus(executionId);
                            latest[0] = status;
                            assertEquals(expectedWaiting, status.isWaiting());
                            assertEquals(expectedComplete, status.isComplete());
                        });
        return latest[0];
    }

    private static String agentResultText(Task task) {
        Object text = task.getOutputData().get("text");
        if (text != null) {
            return String.valueOf(text);
        }
        Object structured = task.getOutputData().get("output");
        if (structured instanceof Map<?, ?> output && output.get("result") != null) {
            return String.valueOf(output.get("result"));
        }
        return "";
    }

    private static void assertControllerDeployedAgent(String agentName) {
        WorkflowDef definition = metadataClient.getWorkflowDef(agentName, 1);
        assertNotNull(definition);
        assertNotNull(definition.getTasks());
        assertTrue(!definition.getTasks().isEmpty(), "AgentController must compile agent tasks");
        assertNotNull(definition.getMetadata());
        assertEquals("conductor", definition.getMetadata().get("agent_sdk"));
        assertTrue(
                definition.getMetadata().get("agentDef") instanceof Map<?, ?>,
                "AgentController must persist the full agentDef");
        Map<?, ?> agentDef = (Map<?, ?>) definition.getMetadata().get("agentDef");
        assertEquals(agentName, agentDef.get("name"));
        assertNotNull(agentDef.get("model"));
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

    private static List<Task> agentTurns(Workflow workflow, String baseReferenceName) {
        return tasksOfType(workflow, "AGENT").stream()
                .filter(
                        task ->
                                task.getReferenceTaskName().equals(baseReferenceName)
                                        || task.getReferenceTaskName()
                                                .startsWith(baseReferenceName + "__"))
                .sorted((left, right) -> Integer.compare(left.getIteration(), right.getIteration()))
                .toList();
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
        deployAgent(
                basicAgentConfig(
                        agentName,
                        "You are a concise test agent. Answer the user's prompt in one sentence."));
    }

    private static void registerSlowAgent(String agentName) {
        deployBlockingAgent(agentName, 120);
    }

    /**
     * An agent blocked on an unpolled {@code SIMPLE} task, with its own {@code
     * WorkflowDef.timeoutSeconds}/{@code TIME_OUT_WF} set so ITS workflow (not any AGENT task
     * wrapping it) times out on its own.
     */
    private static void registerSlowAgentWithOwnTimeout(String agentName, int selfTimeoutSeconds) {
        deployBlockingAgent(agentName, selfTimeoutSeconds);
    }

    private static void registerFailingAgent(String agentName) {
        Map<String, Object> config =
                basicAgentConfig(
                        agentName, "This agent intentionally targets an unknown provider.");
        config.put("model", "unknown_e2e_provider/unknown_model");
        deployAgent(config);
    }

    private static String registerLongRunningAgent(String agentName) {
        return deployBlockingAgent(agentName, 120);
    }

    private static String deployBlockingAgent(String agentName, int timeoutSeconds) {
        String taskType = registerPendingWorkerTask();
        Map<String, Object> config =
                basicAgentConfig(
                        agentName,
                        "Use the prefilled work result as context, then answer in one sentence.");
        config.put("timeoutSeconds", timeoutSeconds);
        config.put("tools", List.of(workerTool(taskType, false)));
        config.put(
                "prefillTools",
                List.of(
                        Map.of(
                                "toolName",
                                taskType,
                                "arguments",
                                Map.of("prompt", "durable work"))));
        deployAgent(config);
        return taskType;
    }

    private static Map<String, Object> approvalAgentConfig(String agentName, String toolName) {
        Map<String, Object> config =
                basicAgentConfig(
                        agentName,
                        "You must call the required work tool exactly once before answering. Do not answer directly.");
        config.put("maxTurns", 4);
        config.put("tools", List.of(workerTool(toolName, true)));
        config.put("requiredTools", List.of(toolName));
        return config;
    }

    private static Map<String, Object> basicAgentConfig(String name, String instructions) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", name);
        config.put("model", MODEL);
        config.put("instructions", instructions);
        config.put("maxTurns", 3);
        config.put("timeoutSeconds", 120);
        config.put("temperature", 0.0);
        return config;
    }

    private static Map<String, Object> workerTool(String name, boolean approvalRequired) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", "Complete deterministic work for the agent lifecycle E2E.");
        tool.put("toolType", "worker");
        tool.put("approvalRequired", approvalRequired);
        tool.put(
                "inputSchema",
                Map.of("type", "object", "properties", Map.of("prompt", Map.of("type", "string"))));
        return tool;
    }

    private static StartResponse deployAgent(Map<String, Object> config) {
        StartResponse response = agentClient.deployAgent(AgentRequest.nativeAgent(config).build());
        assertEquals(config.get("name"), response.getAgentName());
        assertEquals(null, response.getExecutionId(), "deploy must not start an execution");
        assertControllerDeployedAgent(String.valueOf(config.get("name")));
        return response;
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

    /**
     * Registers a workflow with two concrete {@code AGENT} task definitions in a bounded loop. The
     * SET_VARIABLE tasks carry the previous speaker's reply into the next AGENT task, including
     * across loop iterations, so this tests agent-to-agent conversation rather than a multi-agent
     * definition hidden behind one AGENT task.
     */
    private static void registerTwoAgentConversationWorkflow(
            String name, String firstAgent, String secondAgent, int rounds) {
        WorkflowTask initConversation = new WorkflowTask();
        initConversation.setName("set_variable");
        initConversation.setTaskReferenceName("init_conversation");
        initConversation.setType("SET_VARIABLE");
        initConversation.setInputParameters(
                Map.of("lastMessage", "${workflow.input.initialMessage}"));

        WorkflowTask agentA =
                conversationAgentTask(
                        "agent_a",
                        firstAgent,
                        "The other participant said:\n${workflow.variables.lastMessage}\n\nRespond to them directly.");
        WorkflowTask saveAgentA =
                setLastMessageTask("save_agent_a", "${agent_a.output.output.result}");

        WorkflowTask agentB =
                conversationAgentTask(
                        "agent_b",
                        secondAgent,
                        "The other participant said:\n${workflow.variables.lastMessage}\n\nRespond to them directly.");
        WorkflowTask saveAgentB =
                setLastMessageTask("save_agent_b", "${agent_b.output.output.result}");

        WorkflowTask loop = new WorkflowTask();
        loop.setName("agent_chat_loop");
        loop.setTaskReferenceName("agent_chat_loop");
        loop.setType("DO_WHILE");
        loop.setEvaluatorType("graaljs");
        loop.setInputParameters(Map.of("rounds", rounds));
        loop.setLoopCondition(
                "(function(){ return $.agent_chat_loop['iteration'] < $.rounds; })();");
        loop.setLoopOver(List.of(agentA, saveAgentA, agentB, saveAgentB));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTimeoutSeconds(180);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(List.of(initConversation, loop));
        def.setOutputParameters(Map.of("finalReply", "${workflow.variables.lastMessage}"));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static WorkflowTask conversationAgentTask(
            String taskReferenceName, String agentName, String prompt) {
        WorkflowTask task = new WorkflowTask();
        task.setName("AGENT");
        task.setTaskReferenceName(taskReferenceName);
        task.setType("AGENT");
        task.setInputParameters(
                Map.of(
                        "agentType",
                        "conductor",
                        "name",
                        agentName,
                        "prompt",
                        prompt,
                        "pollIntervalSeconds",
                        1,
                        "maxDurationSeconds",
                        120));
        return task;
    }

    private static WorkflowTask setLastMessageTask(String taskReferenceName, String message) {
        WorkflowTask task = new WorkflowTask();
        task.setName("set_variable");
        task.setTaskReferenceName(taskReferenceName);
        task.setType("SET_VARIABLE");
        task.setInputParameters(Map.of("lastMessage", message));
        return task;
    }

    private static void registerCancelAgentWorkflow(String name) {
        WorkflowTask task = new WorkflowTask();
        task.setName("CANCEL_AGENT");
        task.setTaskReferenceName("cancelAgent");
        task.setType("CANCEL_AGENT");
        task.setInputParameters(
                Map.of(
                        "agentType",
                        "conductor",
                        "executionId",
                        "${workflow.input.executionId}",
                        "reason",
                        "${workflow.input.reason}"));
        registerWorkflow(name, task);
    }

    private static void registerWorkflow(String name, WorkflowTask onlyTask) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(onlyTask));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static String registerPendingWorkerTask() {
        return "agent_pending_work_e2e_" + UUID.randomUUID();
    }
}
