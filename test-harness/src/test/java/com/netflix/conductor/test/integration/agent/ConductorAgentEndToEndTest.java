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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
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
 * End-to-end test, through the <b>real engine</b>, of the {@code AGENT} task's {@code conductor}
 * branch: a registered "hello world" agent (a Conductor-native workflow flagged {@code isAgent}) is
 * started by an {@code AGENT} task with {@code agentType=conductor}, driven by the genuine decider
 * + {@link AsyncSystemTaskExecutor} + Redis-backed persistence.
 *
 * <p>This exercises the same wiring as {@code
 * com.netflix.conductor.test.integration.a2a.A2ADurableEngineEndToEndTest}, but for the embedded
 * {@code conductor} agent runtime instead of a remote A2A agent: {@code AgentTask} dispatches to
 * {@code ConductorAgentDelegate}, which calls {@code WorkflowExecutor.startAgentExecution} to
 * resolve the registered agent by name/version ({@code WorkflowDef.isAgent()} + {@code
 * metadata.agentDef}) and starts it as an ordinary child workflow, then polls it to completion.
 *
 * <p>The "hello world" agent has no LLM/tool dependency — its only task is a synchronous {@code
 * INLINE} script that echoes the prompt back as {@code text}. This keeps the test deterministic and
 * free of external provider keys while still exercising the real agent-registration and start/poll
 * machinery end to end.
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
class ConductorAgentEndToEndTest {

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
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "conductor-agent-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "conductor-agent-e2e");
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
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private java.util.Set<WorkflowSystemTask> asyncSystemTasks;

    @Test
    void callConductorAgentRunsRegisteredAgentToCompletion() {
        String agentName = "hello_world_agent_" + UUID.randomUUID();
        registerHelloWorldAgent(agentName);

        String wfName = "conductor_agent_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName);

        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertNotNull(agentTask, "AGENT task must be present on the completed workflow");
        assertEquals("COMPLETED", agentTask.getOutputData().get("state"));
        assertEquals(agentName, agentTask.getOutputData().get("agentName"));
        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertNotNull(
                executionId, "executionId of the started hello-world agent run must be surfaced");
        assertEquals(
                executionId,
                agentTask.getSubWorkflowId(),
                "AGENT must expose subWorkflowId like SUB_WORKFLOW so the UI can open the agent"
                        + " run");
        assertTrue(
                String.valueOf(agentTask.getOutputData().get("text"))
                        .contains("Hello, world! You said: are you there?"),
                "the agent's echoed text should reach the AGENT task output: "
                        + agentTask.getOutputData().get("text"));
        assertNull(
                agentTask.getOutputData().get("sessionId"),
                "sessionId is dead output — never consumed downstream — and must not be surfaced");

        // The hello-world agent execution is itself an ordinary, independently-completed workflow.
        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        assertEquals(agentName, agentExecution.getWorkflowName());
    }

    /**
     * A long-running agent (three {@code WAIT}/{@code INLINE} pairs, ~6s total) that forces the
     * {@code AGENT} task through several {@code execute()} poll cycles — not just a single
     * start-then-immediately-complete pass — while staying deterministic and free of external
     * dependencies (no LLM calls, no sleeps in test code; the engine's own WAIT task drives the
     * delay).
     */
    @Test
    void callConductorAgentPollsMultipleTimesForLongRunningAgent() {
        String agentName = "long_running_agent_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String wfName = "conductor_agent_long_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName);

        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertNotNull(agentTask, "AGENT task must be present on the completed workflow");
        assertEquals("COMPLETED", agentTask.getOutputData().get("state"));
        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertEquals(
                executionId,
                agentTask.getSubWorkflowId(),
                "subWorkflowId must stay stable across every poll of a long-running agent");
        assertTrue(
                String.valueOf(agentTask.getOutputData().get("text"))
                        .contains("Long-running agent finished after 3 waits."),
                "final agent text should reach the AGENT task output: "
                        + agentTask.getOutputData().get("text"));

        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        // The child ran all 6 tasks (3 real WAIT delays, ~6s). Because AGENT is synchronous, its
        // execute() is re-invoked by the decider on each sweep — a single immediate poll right
        // after start() would have seen the child still on its first WAIT, not COMPLETED. So the
        // child having run to completion with all pairs done is itself proof the task was polled
        // across multiple cycles. (pollCount is not applicable: it is only incremented on the
        // async system-task queue path, which a synchronous AGENT no longer uses.)
        assertEquals(6, agentExecution.getTasks().size(), "all 3 WAIT/INLINE pairs must have run");
    }

    /**
     * The money-shot conversation test: a {@code DO_WHILE} loop whose body is just two tasks —
     * {@code AGENT} then {@code HUMAN} — makes a long-running conductor agent and a "human"
     * actually talk to each other over several turns, driving every branch of {@code AgentTask}
     * that the earlier tests don't reach: the <b>resume</b> path ({@code start()} with a non-blank
     * {@code executionId}, which calls {@link
     * org.conductoross.conductor.ai.agent.ConductorAgentDelegate#start}'s {@code respond()} branch)
     * and the {@code WAITING} branch of {@code applyExecution} (surfacing {@code
     * pendingTool}/{@code text} and completing the Conductor task while the underlying run is
     * merely paused, not finished).
     *
     * <p>The registered agent ({@link #registerConversationAgent}) asks two questions, each
     * preceded by its own {@code WAIT(2s)} "thinking" step (so each turn also re-exercises the
     * multi-poll behaviour from {@link #callConductorAgentPollsMultipleTimesForLongRunningAgent}).
     * The loop body's {@code chat} task resumes itself via {@code ${chat.output.executionId}} and
     * feeds the previous turn's human answer via {@code ${human.output.answer}} — both are
     * loop-body self/cross-iteration references, which {@code ParametersUtils.getTaskInputV2}
     * resolves by stripping the {@code __N} iteration suffix DO_WHILE appends, so they always read
     * the most recently completed same-named task (see {@code
     * ai/examples/32-conductor-agent-human-in-loop.json} for the same idiom applied to a single
     * SWITCH-routed pause instead of a loop). The loop condition stops once {@code chat} reports
     * anything other than {@code WAITING}.
     */
    @Test
    void conversationLoopAlternatesLongRunningAgentAndHumanUntilComplete() {
        String agentName = "conversation_agent_" + UUID.randomUUID();
        registerConversationAgent(agentName);

        String wfName = "conductor_agent_conversation_e2e_" + UUID.randomUUID();
        registerConversationLoopWorkflow(wfName, agentName);

        Map<String, Object> input = new HashMap<>();
        input.put("initialPrompt", "Hi, I'd like to chat.");
        String workflowId = startWorkflow(wfName, input);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());

        List<Task> chatCalls = tasksOfType(completed, "AGENT");
        List<Task> humanTurns = tasksOfType(completed, "HUMAN");
        assertEquals(
                3,
                chatCalls.size(),
                "one fresh start + two resumes: a call per question asked, plus the final "
                        + "completing call");
        assertEquals(3, humanTurns.size(), "one human turn per AGENT call, including the last");

        // Turn 1: fresh start — asks for a name and pauses. While WAITING the agent's own
        // workflow isn't terminal, so ConductorAgentDelegate.getStatus() has no `output` to read
        // `text` from yet — the question only ever surfaces via `pendingTool`.
        Task turn1 = chatCalls.get(0);
        assertEquals("WAITING", turn1.getOutputData().get("state"));
        assertTrue(
                pendingQuestion(turn1).contains("What is your name?"),
                "turn 1 should ask for a name: " + pendingQuestion(turn1));
        String executionId = (String) turn1.getOutputData().get("executionId");
        assertNotNull(executionId);
        assertEquals(executionId, turn1.getSubWorkflowId());

        // Turn 2: resumed with "Alice" — asks for a favorite color and pauses again.
        Task turn2 = chatCalls.get(1);
        assertEquals("WAITING", turn2.getOutputData().get("state"));
        assertEquals(
                executionId,
                turn2.getOutputData().get("executionId"),
                "resume must reattach to the SAME child execution, never start a new one");
        assertTrue(
                pendingQuestion(turn2).contains("favorite color"),
                "turn 2 should ask for a favorite color: " + pendingQuestion(turn2));

        // Turn 3: resumed with "blue" — the agent synthesizes both answers and completes.
        Task turn3 = chatCalls.get(2);
        assertEquals("COMPLETED", turn3.getOutputData().get("state"));
        assertEquals(executionId, turn3.getOutputData().get("executionId"));
        assertTrue(
                String.valueOf(turn3.getOutputData().get("text"))
                        .contains("Thanks Alice! I will remember your favorite color is blue."),
                "final synthesis should reference both answers: "
                        + turn3.getOutputData().get("text"));

        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, agentExecution.getStatus());
        assertEquals(
                8,
                agentExecution.getTasks().size(),
                "3x(WAIT+step) plus the 2 internal HUMAN pauses the agent itself asked");
    }

    /**
     * Exercises {@code AgentTask.cancel()} / {@code ConductorAgentDelegate.cancel()}: terminating
     * the parent workflow while the {@code chat} AGENT task is still mid-flight (started, not yet
     * {@code WAITING}/completed) must propagate the termination to the in-flight conductor agent
     * execution — the same "the agent is a sub-workflow, so cancel it like one" contract that
     * {@code AgentTask.subWorkflowId} advertises to the UI.
     */
    @Test
    void terminatingParentWorkflowCancelsInFlightConductorAgent() {
        String agentName = "conversation_agent_cancel_" + UUID.randomUUID();
        registerConversationAgent(agentName);

        String wfName = "conductor_agent_cancel_e2e_" + UUID.randomUUID();
        registerConversationLoopWorkflow(wfName, agentName);

        Map<String, Object> input = new HashMap<>();
        input.put("initialPrompt", "Hi, I'd like to chat.");
        String workflowId = startWorkflow(wfName, input);

        // Capture the child execution id the instant the first turn is in flight — started
        // (executionId known) but not yet WAITING/completed, i.e. still inside its first
        // WAIT/INLINE "thinking" pair.
        AtomicReference<String> executionIdRef = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue("WAIT");
                            Task chat =
                                    agentTaskOf(
                                            executionService.getExecutionStatus(workflowId, true));
                            if (chat == null || chat.getStatus() != Task.Status.IN_PROGRESS) {
                                return false;
                            }
                            Object executionId = chat.getOutputData().get("executionId");
                            if (executionId == null) {
                                return false;
                            }
                            executionIdRef.set((String) executionId);
                            return true;
                        });

        String executionId = executionIdRef.get();
        assertNotNull(executionId, "must have captured the in-flight child execution id");
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                executionService.getExecutionStatus(executionId, false).getStatus(),
                "sanity check: the child conversation agent must still be genuinely running");

        workflowExecutor.terminateWorkflow(workflowId, "test: cancel mid-conversation");

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () ->
                                executionService
                                        .getExecutionStatus(executionId, false)
                                        .getStatus()
                                        .isTerminal());

        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                executionService.getExecutionStatus(executionId, false).getStatus(),
                "cancel() must propagate termination to the in-flight conductor agent execution");
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                executionService.getExecutionStatus(workflowId, true).getStatus());
    }

    /**
     * Exercises Guard 1 (absolute deadline) from {@code ConductorAgentDelegate}'s class javadoc: a
     * still-{@code RUNNING} child that never reaches a terminal state within {@code
     * maxDurationSeconds} must fail the AGENT task terminally, through the real decider/timer — not
     * the mocked {@code execute_deadlineExceededFailsTerminally} unit test.
     *
     * <p>Also asserts that the deadline guard propagates a best-effort termination to the child
     * execution — an over-budget agent run must not be left orphaned and {@code RUNNING}. (Same
     * contract as {@link #terminatingParentWorkflowCancelsInFlightConductorAgent}, where an
     * explicit outer {@code cancel} propagates; here it is the liveness guard that does.)
     */
    @Test
    void agentExceedingMaxDurationFailsTerminallyAndTerminatesTheChild() {
        String agentName = "slow_agent_" + UUID.randomUUID();
        registerSlowAgent(agentName, "10 seconds");

        String wfName = "conductor_agent_timeout_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName, Map.of("maxDurationSeconds", 3));
        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, agentTask.getStatus());
        assertTrue(
                agentTask.getReasonForIncompletion().contains("exceeded max duration of 3s"),
                "reason: " + agentTask.getReasonForIncompletion());

        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertNotNull(executionId);
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                executionService.getExecutionStatus(executionId, false).getStatus(),
                "the deadline guard must terminate the child, not leave it orphaned/running");
    }

    /**
     * When the registered agent's own workflow fails (not paused, not timed out — genuinely
     * FAILED), {@code applyExecution}'s FAILED branch must map that onto the AGENT task, through
     * the real engine.
     */
    @Test
    void agentInternalFailureMapsToFailedOnTheAgentTask() {
        String agentName = "failing_agent_" + UUID.randomUUID();
        registerFailingAgent(agentName);

        String wfName = "conductor_agent_failure_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName);
        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertNotNull(agentTask.getReasonForIncompletion());

        String executionId = (String) agentTask.getOutputData().get("executionId");
        assertEquals(
                Workflow.WorkflowStatus.FAILED,
                executionService.getExecutionStatus(executionId, false).getStatus());
    }

    /**
     * Referencing an {@code agentName} that was never registered must fail through the real {@code
     * MetadataDAO} lookup miss inside {@code WorkflowExecutorOps.startAgentExecution} — {@code
     * NotFoundException} isn't a {@code NonRetryableException}, so it falls through {@code
     * ConductorAgentDelegate.start()}'s generic catch branch (retryable {@code FAILED}, not
     * terminal) rather than the blank-agentName validation path.
     */
    @Test
    void referencingUnregisteredAgentFailsWithNotFound() {
        String bogusAgentName = "does_not_exist_" + UUID.randomUUID();
        String wfName = "conductor_agent_missing_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, bogusAgentName);
        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertTrue(
                agentTask.getReasonForIncompletion().contains("Agent not found"),
                "reason: " + agentTask.getReasonForIncompletion());
    }

    /**
     * The reverse direction of {@link #terminatingParentWorkflowCancelsInFlightConductorAgent}: the
     * child execution is terminated <b>independently</b> (an operator, or unrelated tooling,
     * canceling it directly — not via the AGENT task's own {@code cancel()}), and the still-polling
     * AGENT task must observe {@code TERMINATED} on its next poll and map it to {@code CANCELED}
     * via {@code deriveState}, through the real engine.
     */
    @Test
    void childCanceledIndependentlySurfacesAsCanceledOnNextPoll() {
        String agentName = "long_running_agent_extcancel_" + UUID.randomUUID();
        registerLongRunningAgent(agentName);

        String wfName = "conductor_agent_extcancel_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agentName);
        String workflowId = startWorkflow(wfName);

        AtomicReference<String> executionIdRef = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue("WAIT");
                            Task chat =
                                    agentTaskOf(
                                            executionService.getExecutionStatus(workflowId, true));
                            Object executionId =
                                    chat != null ? chat.getOutputData().get("executionId") : null;
                            if (executionId == null) {
                                return false;
                            }
                            executionIdRef.set((String) executionId);
                            return true;
                        });

        String executionId = executionIdRef.get();
        workflowExecutor.terminateWorkflow(
                executionId, "operator canceled the agent execution directly");

        Workflow completed = awaitTerminal(workflowId);

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.CANCELED, agentTask.getStatus());
        assertEquals("CANCELED", agentTask.getOutputData().get("state"));
        assertEquals(
                "operator canceled the agent execution directly",
                agentTask.getReasonForIncompletion());
    }

    /**
     * The agent <b>itself</b> timing out — distinct from every other timeout scenario here:
     *
     * <ul>
     *   <li>{@link #agentExceedingMaxDurationFailsTerminallyWithoutCancelingTheChild} is the AGENT
     *       <i>wrapper</i> task's own {@code maxDurationSeconds} deadline, enforced by {@code
     *       ConductorAgentDelegate.execute()} while the child is merely {@code RUNNING}.
     *   <li>A per-task {@code TaskDef.responseTimeoutSeconds} (tried and abandoned in the {@code
     *       e2e} module's {@code AgentTaskTests}) never fires for a system task like AGENT/WAIT —
     *       that mechanism assumes an external worker polling and going silent, which doesn't apply
     *       to tasks the engine drives internally.
     * </ul>
     *
     * <p>Here the registered agent's <b>own</b> {@code WorkflowDef.timeoutSeconds} +{@code
     * timeoutPolicy=TIME_OUT_WF} fires — a completely different, task-type-agnostic mechanism
     * ({@code DeciderService.checkWorkflowTimeout}, purely wall-clock: {@code workflow
     * .getCreateTime()} vs. {@code workflowDef.getTimeoutSeconds()}). The child's own workflow ends
     * {@code TIMED_OUT}; {@code ConductorAgentDelegate.deriveState} explicitly groups {@code
     * TIMED_OUT} with {@code FAILED}, so the wrapper AGENT task must land on plain {@code FAILED}
     * (not {@code FAILED_WITH_TERMINAL_ERROR}) with the child's own timeout message.
     */
    @Test
    void agentsOwnWorkflowTimeoutMapsToFailedOnTheAgentTask() {
        String agentName = "self_timeout_agent_" + UUID.randomUUID();
        registerSlowAgentWithOwnTimeout(agentName, "10 seconds", 3);

        String wfName = "conductor_agent_selftimeout_e2e_" + UUID.randomUUID();
        // Generous maxDurationSeconds so the WRAPPER's own deadline guard can't race with (or
        // mask) the CHILD's much shorter self-timeout.
        registerCallAgentWorkflow(wfName, agentName, Map.of("maxDurationSeconds", 60));
        String workflowId = startWorkflow(wfName);

        Workflow completed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());

        Task agentTask = agentTaskOf(completed);
        assertEquals(Task.Status.FAILED, agentTask.getStatus());
        assertEquals("FAILED", agentTask.getOutputData().get("state"));
        assertTrue(
                agentTask.getReasonForIncompletion().contains("Workflow timed out after"),
                "reason: " + agentTask.getReasonForIncompletion());

        String executionId = (String) agentTask.getOutputData().get("executionId");
        Workflow agentExecution = executionService.getExecutionStatus(executionId, true);
        assertEquals(Workflow.WorkflowStatus.TIMED_OUT, agentExecution.getStatus());
        // Deliberately not asserting on the child's own WAIT task status here: whether it ends
        // CANCELED (checkWorkflowTimeout wins while it's still IN_PROGRESS, triggering
        // cancelNonTerminalTasks) or COMPLETED (the WAIT elapses and finalizes in an earlier
        // decide() cycle, and checkWorkflowTimeout's unconditional-first check overrides the
        // still-not-yet-finalized workflow status to TIMED_OUT before the natural COMPLETED path
        // catches up) depends on a real race in the engine's own decide() cadence, observed
        // directly: this assertion flipped between runs against the identical code.
    }

    // ── engine helpers ─────────────────────────────────────────────────────

    private Workflow awaitTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue("WAIT");
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
     * Stands in for the "human" side of the conversation. If the workflow's most recent {@code
     * HUMAN} task is {@code IN_PROGRESS}, reads the pending question off the most recent {@code
     * AGENT} task's {@code pendingTool} output (the same field a real chat UI would render) and
     * completes the HUMAN task the same way {@code TaskServiceImpl.updateTask}/{@code
     * ConductorAgentDelegate.respond} do — a plain {@code TaskResult} update, since HUMAN never
     * self-completes (it has no {@code execute()}; see {@code core.execution.tasks.Human}). A no-op
     * for workflows with no HUMAN task (the other two tests in this class).
     */
    private void answerPendingHumanTask(String workflowId) {
        Workflow workflow = executionService.getExecutionStatus(workflowId, true);
        if (workflow == null || workflow.getTasks() == null) {
            return;
        }
        Task human = lastTaskOfType(workflow, "HUMAN");
        if (human == null || human.getStatus() != Task.Status.IN_PROGRESS) {
            return;
        }

        String answer = "ok, thanks!";
        Task chat = lastTaskOfType(workflow, "AGENT");
        String question = chat != null ? pendingQuestion(chat) : "";
        if (question.contains("name")) {
            answer = "Alice";
        } else if (question.contains("favorite color")) {
            answer = "blue";
        }

        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(human.getTaskId());
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(Map.of("answer", answer));
        workflowExecutor.updateTask(taskResult);
    }

    private Task lastTaskOfType(Workflow workflow, String taskType) {
        return tasksOfType(workflow, taskType).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private List<Task> tasksOfType(Workflow workflow, String taskType) {
        if (workflow == null || workflow.getTasks() == null) {
            return List.of();
        }
        return workflow.getTasks().stream()
                .filter(t -> taskType.equals(t.getTaskType()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * The agent's question while {@code WAITING}, read from {@code pendingTool.parameters.question}
     * — NOT {@code text}, which {@code ConductorAgentDelegate.getStatus()} only ever populates from
     * a <b>terminal</b> workflow's output, and the agent's own workflow is merely paused (still
     * {@code RUNNING}) while waiting on an internal HUMAN task.
     */
    private String pendingQuestion(Task chat) {
        Object pendingTool = chat.getOutputData().get("pendingTool");
        if (pendingTool instanceof Map<?, ?> pending
                && pending.get("parameters") instanceof Map<?, ?> parameters) {
            return String.valueOf(parameters.get("question"));
        }
        return "";
    }

    /**
     * Drains the async {@code WAIT} system-task queue (used by the child agent's own "thinking"
     * steps) via {@link AsyncSystemTaskExecutor} — the same path {@code
     * SystemTaskWorkerCoordinator} takes in production. Needed because {@code
     * conductor.system-task-workers.enabled=false} in test mode disables the automatic coordinator,
     * so nothing else pops these queues; the long-running agent's own {@code WAIT} steps would
     * otherwise never advance. The {@code AGENT} task itself is synchronous and is driven by {@code
     * workflowExecutor.decide(workflowId)} instead.
     */
    private void drainQueue(String taskType) {
        WorkflowSystemTask task =
                asyncSystemTasks.stream()
                        .filter(t -> taskType.equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                taskType
                                                        + " WorkflowSystemTask was not registered —"
                                                        + " conductor.integrations.ai.enabled must be"
                                                        + " true and the ai module on the classpath."));
        for (String taskId : queueDAO.pop(taskType, 5, 100)) {
            asyncSystemTaskExecutor.execute(task, taskId);
        }
    }

    private Task agentTaskOf(Workflow workflow) {
        if (workflow == null || workflow.getTasks() == null) {
            return null;
        }
        return workflow.getTasks().stream()
                .filter(t -> "AGENT".equals(t.getTaskType()))
                .findFirst()
                .orElse(null);
    }

    private String startWorkflow(String name) {
        return startWorkflow(name, new HashMap<>());
    }

    private String startWorkflow(String name, Map<String, Object> input) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(name);
        swi.setVersion(1);
        swi.setWorkflowInput(input);
        return workflowExecutor.startWorkflow(swi);
    }

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Registers a minimal "hello world" agent: a workflow definition flagged as an agent (via
     * {@code metadata.agentDef}, mirroring what the agentspan compiler would produce) whose only
     * task is a synchronous {@code INLINE} script that echoes the caller's prompt back as {@code
     * text}. No LLM or tool dependency, so {@code WorkflowExecutor.startAgentExecution} runs it to
     * completion synchronously when started.
     */
    private void registerHelloWorldAgent(String agentName) {
        ensureTaskDef("INLINE");

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
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(hello));
        def.setOutputParameters(Map.of("text", "${hello.output.result.text}"));
        // Flags this WorkflowDef as a registered agent — WorkflowDef.isAgent() checks for a
        // non-null metadata.agentDef, and WorkflowExecutorOps.startAgentExecution converts this
        // map into an AgentConfig. No fields beyond 'name' are needed since this agent has no
        // tools/sub-agents/strategy that would otherwise be read from the config.
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * Registers a "long-running" agent: three {@code WAIT(2s)} / {@code INLINE} pairs (~6s total,
     * all native system tasks — no worker, no LLM), flagged as an agent the same way {@link
     * #registerHelloWorldAgent} is. Used to prove the {@code AGENT} task actually re-polls a
     * still-running execution multiple times rather than only handling the immediate-completion
     * case.
     */
    private void registerLongRunningAgent(String agentName) {
        ensureTaskDef("INLINE");
        ensureTaskDef("WAIT");

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
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(tasks);
        def.setOutputParameters(Map.of("text", "${step3.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /** A single {@code WAIT(duration)} agent — deliberately slower than any deadline under test. */
    private void registerSlowAgent(String agentName, String waitDuration) {
        ensureTaskDef("WAIT");

        WorkflowTask wait = waitTask("wait");
        wait.setInputParameters(Map.of("duration", waitDuration));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(wait));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * A {@code WAIT(waitDuration)} agent slower than {@code selfTimeoutSeconds}, with its own
     * {@code WorkflowDef.timeoutSeconds}/{@code TIME_OUT_WF} set so ITS workflow (not any AGENT
     * task wrapping it) times out on its own.
     */
    private void registerSlowAgentWithOwnTimeout(
            String agentName, String waitDuration, int selfTimeoutSeconds) {
        ensureTaskDef("WAIT");

        WorkflowTask wait = waitTask("wait");
        wait.setInputParameters(Map.of("duration", waitDuration));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(wait));
        def.setTimeoutSeconds(selfTimeoutSeconds);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * An agent whose only task fails deterministically: an unknown {@code evaluatorType} trips
     * {@code Inline.checkEvaluatorType}'s {@code TerminateWorkflowException}, which fails the task
     * (and, since it's non-optional, the whole agent workflow) with {@code
     * FAILED_WITH_TERMINAL_ERROR} — no retries, no flakiness.
     */
    private void registerFailingAgent(String agentName) {
        ensureTaskDef("INLINE");

        WorkflowTask boom = new WorkflowTask();
        boom.setName("INLINE");
        boom.setTaskReferenceName("boom");
        boom.setType("INLINE");
        boom.setInputParameters(Map.of("evaluatorType", "not_a_real_evaluator", "expression", "1"));

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(boom));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private void registerCallAgentWorkflow(String name, String agentName) {
        registerCallAgentWorkflow(name, agentName, Map.of());
    }

    private void registerCallAgentWorkflow(
            String name, String agentName, Map<String, Object> extraInput) {
        ensureTaskDef("AGENT");

        WorkflowTask task = new WorkflowTask();
        task.setName("AGENT");
        task.setTaskReferenceName("callAgent");
        task.setType("AGENT");
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("agentType", "conductor");
        taskInput.put("name", agentName);
        taskInput.put("prompt", "are you there?");
        taskInput.put("pollIntervalSeconds", 1);
        taskInput.putAll(extraInput);
        task.setInputParameters(taskInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * Registers a small two-question "interview" agent: {@code WAIT(2s)} + {@code INLINE} "think"
     * pairs bracket two internal {@code HUMAN} pauses, each carrying its question as {@code
     * tool_name}/{@code parameters.question} — exactly the shape {@code
     * ConductorAgentDelegate#pendingTool} surfaces to the AGENT task's caller. Entirely native
     * system tasks (no LLM, no worker), so the whole conversation is deterministic:
     *
     * <ol>
     *   <li>{@code wait1} + {@code greet} → asks "What is your name?" → {@code ask_name} (HUMAN)
     *   <li>{@code wait2} + {@code followup} (using the name) → asks "...favorite color?" → {@code
     *       ask_color} (HUMAN)
     *   <li>{@code wait3} + {@code finalize} (using both answers) → completes
     * </ol>
     */
    private void registerConversationAgent(String agentName) {
        ensureTaskDef("INLINE");
        ensureTaskDef("WAIT");
        ensureTaskDef("HUMAN");

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
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(wait1, greet, askName, wait2, followup, askColor, wait3, finalize));
        def.setOutputParameters(Map.of("text", "${finalize.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * Registers the "conversation" caller workflow: a {@code DO_WHILE} loop whose entire body is
     * {@code chat} (AGENT) then {@code human} (HUMAN). {@code chat} resumes itself via {@code
     * ${chat.output.executionId}} and carries the previous turn's answer via {@code
     * ${human.output.answer}} — both resolve to the latest completed same-named loop-body task
     * regardless of iteration (see class javadoc). The loop stops the instant {@code chat} reports
     * anything other than {@code WAITING}; {@code $.loop['iteration'] < 6} is just a safety net
     * against an infinite loop if that ever regresses.
     */
    private void registerConversationLoopWorkflow(String name, String agentName) {
        ensureTaskDef("AGENT");
        ensureTaskDef("HUMAN");
        ensureTaskDef("INLINE");

        // No more message>parts>text>prompt fallback (ConductorAgentRequest has a single `prompt`
        // field), so the "unresolved-on-first-iteration falls through to the initial prompt" idiom
        // that `text`/`prompt` used to provide has to be computed explicitly: this INLINE resolves
        // the effective prompt with a JS ternary before `chat` runs, mirroring
        // ${chat.output.executionId}`'s own reliance on an unresolved self-reference being falsy on
        // the first loop iteration.
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
        chat.setInputParameters(chatInput);

        WorkflowTask human = new WorkflowTask();
        human.setName("HUMAN");
        human.setTaskReferenceName("human");
        human.setType("HUMAN");
        // Purely for observability (e.g. a real UI rendering this workflow) — the test driver
        // reads the question straight off the "chat" task's own output, not from here.
        human.setInputParameters(Map.of("pendingQuestion", "${chat.output.pendingTool}"));

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        // Unlike ParametersUtils' general ${ref.output.x} resolution, DoWhile.evaluateCondition
        // binds each loop-body ref name directly to that task's outputData map (not wrapped in an
        // {input,output,...} envelope) — so this reads $.chat['state'], not
        // $.chat['output']['state'].
        loop.setLoopCondition(
                "if ( $.chat['state'] == 'WAITING' && $.loop['iteration'] < 6 ) {"
                        + " true; } else { false; }");
        loop.setLoopOver(List.of(resolvePrompt, chat, human));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("conductor-agent-e2e@conductor.test");
        def.setTasks(List.of(loop));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private WorkflowTask waitTask(String refName) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("WAIT");
        wait.setTaskReferenceName(refName);
        wait.setType("WAIT");
        wait.setInputParameters(Map.of("duration", "2 seconds"));
        return wait;
    }

    private WorkflowTask inlineTask(String refName, Map<String, Object> extraInput) {
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

    /** A HUMAN task whose question is surfaced through {@code pendingTool} (see class javadoc). */
    private WorkflowTask humanQuestionTask(String refName, String questionExpression) {
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

    private void ensureTaskDef(String taskType) {
        TaskDef td = new TaskDef();
        td.setName(taskType);
        td.setRetryCount(0);
        td.setTimeoutSeconds(120);
        try {
            metadataService.registerTaskDef(List.of(td));
        } catch (Exception ignored) {
            // already registered by a prior test
        }
    }
}
