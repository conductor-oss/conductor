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
package org.conductoross.conductor.ai.agent;

import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.AgentTask;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durability/guard coverage for the {@code AGENT} (conductor) branch, driven through the real
 * {@link AgentTask} entry points against an in-process {@link FakeWorkflowExecutor} — the
 * conductor-branch analogue of {@code A2ADurabilityTest} (no mock frameworks). Proves the
 * idempotency key shape, the two liveness guards, missing-executor failure, and cancel propagation
 * (test-plan.md §4.2).
 */
class ConductorAgentDurabilityTest {

    private static AgentTask agentTask() {
        return new AgentTask(new A2AService(new OkHttpClient()), new StandardEnvironment());
    }

    private static WorkflowModel workflow(WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("exec-1");
        WorkflowDef definition = new WorkflowDef();
        definition.setName("planner");
        workflow.setWorkflowDefinition(definition);
        workflow.setStatus(status);
        workflow.setInput(Map.of("session_id", "sess-1"));
        return workflow;
    }

    private static TaskModel taskModel(Map<String, Object> input, int iteration) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        model.setTaskId("conductor-task-1");
        model.setWorkflowInstanceId("wf-1");
        model.setReferenceTaskName("agent_ref");
        model.setIteration(iteration);
        return model;
    }

    private static Map<String, Object> conductorInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("name", "planner");
        input.put("prompt", "plan my trip");
        return input;
    }

    // Idempotency (invariant 5): the key is byte-for-byte "conductor-agent-<wf>:<ref>:<iter>" and
    // is
    // stable across two starts on the same identity; changing the iteration changes the key.
    @Test
    void idempotencyKey_isStableAcrossRetriesAndIterationSensitive() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        AgentTask task = agentTask();

        task.start(null, taskModel(conductorInput(), 0), executor);
        String first = executor.lastStartRequest.getIdempotencyKey();

        // A retry of the same logical call (same wf/ref/iteration) reissues the identical key so
        // the runtime dedupes it.
        task.start(null, taskModel(conductorInput(), 0), executor);
        String retry = executor.lastStartRequest.getIdempotencyKey();

        assertEquals("conductor-agent-wf-1:agent_ref:0", first);
        assertEquals(first, retry);

        // A different DO_WHILE iteration is a genuinely distinct run and gets a distinct key.
        task.start(null, taskModel(conductorInput(), 3), executor);
        String otherIteration = executor.lastStartRequest.getIdempotencyKey();

        assertEquals("conductor-agent-wf-1:agent_ref:3", otherIteration);
        assertNotEquals(first, otherIteration);
    }

    // Guard 1 (absolute deadline): a back-dated agentStartedAt past a small maxDurationSeconds
    // fails
    // the task terminally on the next poll.
    @Test
    void guard1_deadlineExceeded_failsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.RUNNING);
        AgentTask task = agentTask();

        Map<String, Object> input = conductorInput();
        input.put("maxDurationSeconds", 1);
        TaskModel model = taskModel(input, 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis() - 10_000L);

        boolean changed = task.execute(null, model, executor);

        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertTrue(model.getReasonForIncompletion().contains("max duration"));
    }

    // Guard 2 (poll-failure cap, invariant 3): consecutive getStatus failures increment
    // agentPollFailures and fail the task terminally at the default cap of 30.
    @Test
    void guard2_pollFailureCap_failsTerminallyAtDefaultCap() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.throwOnGetWorkflow = true;
        AgentTask task = agentTask();

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        // The first 29 failures are transient: the counter climbs but the task keeps polling.
        for (int i = 1; i < 30; i++) {
            boolean changed = task.execute(null, model, executor);
            assertFalse(changed, "transient poll failure " + i + " must keep polling");
            assertEquals(
                    i,
                    ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                            .intValue());
        }

        // The 30th consecutive failure hits the cap and fails terminally.
        boolean changed = task.execute(null, model, executor);
        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertTrue(model.getReasonForIncompletion().contains("consecutive poll failures"));
    }

    // Guard 2 reset: any successful poll clears the consecutive-failure counter back to 0.
    @Test
    void guard2_successResetsPollFailureCounter() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.throwOnGetWorkflow = true;
        AgentTask task = agentTask();

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        task.execute(null, model, executor);
        task.execute(null, model, executor);
        task.execute(null, model, executor);
        assertEquals(
                3,
                ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());

        // A subsequent success resets the counter and returns the task to polling.
        executor.throwOnGetWorkflow = false;
        executor.workflow = workflow(WorkflowModel.Status.RUNNING);
        boolean changed = task.execute(null, model, executor);

        assertFalse(changed);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
        assertEquals(
                0,
                ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());
    }

    // Missing executor: lifecycle misuse fails terminally instead of dereferencing null.
    @Test
    void missingExecutor_failsTerminally() {
        AgentTask task = agentTask();

        TaskModel model = taskModel(conductorInput(), 0);
        task.start(null, model, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertEquals(
                "Conductor agent execution requires a WorkflowExecutor",
                model.getReasonForIncompletion());
    }

    // Cancel propagates the reason to the runtime and marks the task CANCELED.
    @Test
    void cancel_propagatesReasonAndMarksCanceled() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        AgentTask task = agentTask();

        TaskModel model = taskModel(conductorInput(), 0);
        task.start(null, model, executor); // anchors the executionId in the task output
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());

        task.cancel(null, model, executor);

        assertEquals("exec-1", executor.lastTerminatedExecutionId);
        assertEquals("workflow canceled", executor.lastTerminationReason);
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    // Best-effort cancel: even if the runtime throws, the task is still marked CANCELED.
    @Test
    void cancel_bestEffortWhenRuntimeThrows() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.throwOnTerminate = true;
        AgentTask task = agentTask();

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        task.cancel(null, model, executor);

        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }
}
