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
import java.util.Optional;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.AgentTask;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.netflix.conductor.model.TaskModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durability/guard coverage for the {@code AGENT} (conductor) branch, driven through the real
 * {@link AgentTask} entry points against an in-process {@link FakeConductorAgentRuntime} — the
 * conductor-branch analogue of {@code A2ADurabilityTest} (no mock frameworks). Proves the
 * idempotency key shape, the two liveness guards, runtime-absent failure, and cancel propagation
 * (test-plan.md §4.2).
 */
class ConductorAgentDurabilityTest {

    private static AgentTask agentTask(Optional<ConductorAgentRuntime> runtime) {
        return new AgentTask(
                new A2AService(new OkHttpClient()), new StandardEnvironment(), runtime);
    }

    private static ConductorAgentExecution execution(ConductorAgentState state) {
        return ConductorAgentExecution.builder()
                .executionId("exec-1")
                .agentName("planner")
                .sessionId("sess-1")
                .state(state)
                .build();
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
        input.put("agentName", "planner");
        input.put("text", "plan my trip");
        return input;
    }

    // Idempotency (invariant 5): the key is byte-for-byte "conductor-agent-<wf>:<ref>:<iter>" and
    // is
    // stable across two starts on the same identity; changing the iteration changes the key.
    @Test
    void idempotencyKey_isStableAcrossRetriesAndIterationSensitive() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        AgentTask task = agentTask(Optional.of(runtime));

        task.start(null, taskModel(conductorInput(), 0), null);
        String first = runtime.lastStartRequest.getIdempotencyKey();

        // A retry of the same logical call (same wf/ref/iteration) reissues the identical key so
        // the runtime dedupes it.
        task.start(null, taskModel(conductorInput(), 0), null);
        String retry = runtime.lastStartRequest.getIdempotencyKey();

        assertEquals("conductor-agent-wf-1:agent_ref:0", first);
        assertEquals(first, retry);

        // A different DO_WHILE iteration is a genuinely distinct run and gets a distinct key.
        task.start(null, taskModel(conductorInput(), 3), null);
        String otherIteration = runtime.lastStartRequest.getIdempotencyKey();

        assertEquals("conductor-agent-wf-1:agent_ref:3", otherIteration);
        assertNotEquals(first, otherIteration);
    }

    // Guard 1 (absolute deadline): a back-dated agentStartedAt past a small maxDurationSeconds
    // fails
    // the task terminally on the next poll.
    @Test
    void guard1_deadlineExceeded_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.statusResult = execution(ConductorAgentState.RUNNING);
        AgentTask task = agentTask(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.put("maxDurationSeconds", 1);
        TaskModel model = taskModel(input, 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis() - 10_000L);

        boolean changed = task.execute(null, model, null);

        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertTrue(model.getReasonForIncompletion().contains("max duration"));
    }

    // Guard 2 (poll-failure cap, invariant 3): consecutive getStatus failures increment
    // agentPollFailures and fail the task terminally at the default cap of 30.
    @Test
    void guard2_pollFailureCap_failsTerminallyAtDefaultCap() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.throwOnStatus = true;
        AgentTask task = agentTask(Optional.of(runtime));

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        // The first 29 failures are transient: the counter climbs but the task keeps polling.
        for (int i = 1; i < 30; i++) {
            boolean changed = task.execute(null, model, null);
            assertFalse(changed, "transient poll failure " + i + " must keep polling");
            assertEquals(
                    i,
                    ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                            .intValue());
        }

        // The 30th consecutive failure hits the cap and fails terminally.
        boolean changed = task.execute(null, model, null);
        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertTrue(model.getReasonForIncompletion().contains("consecutive poll failures"));
    }

    // Guard 2 reset: any successful poll clears the consecutive-failure counter back to 0.
    @Test
    void guard2_successResetsPollFailureCounter() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.throwOnStatus = true;
        AgentTask task = agentTask(Optional.of(runtime));

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        task.execute(null, model, null);
        task.execute(null, model, null);
        task.execute(null, model, null);
        assertEquals(
                3,
                ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());

        // A subsequent success resets the counter and returns the task to polling.
        runtime.throwOnStatus = false;
        runtime.statusResult = execution(ConductorAgentState.RUNNING);
        boolean changed = task.execute(null, model, null);

        assertFalse(changed);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
        assertEquals(
                0,
                ((Number) model.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());
    }

    // Runtime-absent: with no embedded runtime the branch fails terminally with the exact message.
    @Test
    void runtimeAbsent_failsTerminally() {
        AgentTask task = agentTask(Optional.empty());

        TaskModel model = taskModel(conductorInput(), 0);
        task.start(null, model, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertEquals(
                "Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)",
                model.getReasonForIncompletion());
    }

    // Cancel propagates the reason to the runtime and marks the task CANCELED.
    @Test
    void cancel_propagatesReasonAndMarksCanceled() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        AgentTask task = agentTask(Optional.of(runtime));

        TaskModel model = taskModel(conductorInput(), 0);
        task.start(null, model, null); // anchors the executionId in the task output
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());

        task.cancel(null, model, null);

        assertEquals("exec-1", runtime.lastCancelExecutionId);
        assertEquals("workflow canceled", runtime.lastCancelReason);
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    // Best-effort cancel: even if the runtime throws, the task is still marked CANCELED.
    @Test
    void cancel_bestEffortWhenRuntimeThrows() {
        FakeConductorAgentRuntime runtime =
                new FakeConductorAgentRuntime() {
                    @Override
                    public void cancel(String executionId, String reason) {
                        throw new RuntimeException("runtime unreachable");
                    }
                };
        AgentTask task = agentTask(Optional.of(runtime));

        TaskModel model = taskModel(conductorInput(), 0);
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        task.cancel(null, model, null);

        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }
}
