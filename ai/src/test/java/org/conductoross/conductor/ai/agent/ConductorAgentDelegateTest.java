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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ConductorAgentDelegate}, exercising the start/execute/cancel state machine
 * and the two liveness guards against a real (in-package, hand-written) {@link
 * FakeConductorAgentRuntime} — no mock frameworks.
 */
class ConductorAgentDelegateTest {

    /**
     * A real, scriptable {@link ConductorAgentRuntime}. Returns pre-set snapshots and can be told to
     * throw from {@link #getStatus} to drive the transient-failure guard.
     */
    static final class FakeConductorAgentRuntime implements ConductorAgentRuntime {

        ConductorAgentExecution startResult;
        ConductorAgentExecution statusResult;
        boolean throwOnStatus;

        ConductorAgentStartRequest lastStartRequest;
        String lastRespondExecutionId;
        Map<String, Object> lastRespondMessage;
        String lastCancelExecutionId;
        String lastCancelReason;

        @Override
        public ConductorAgentExecution start(ConductorAgentStartRequest request) {
            this.lastStartRequest = request;
            return startResult;
        }

        @Override
        public ConductorAgentExecution getStatus(String executionId) {
            if (throwOnStatus) {
                throw new RuntimeException("runtime unreachable");
            }
            return statusResult != null ? statusResult : startResult;
        }

        @Override
        public void respond(String executionId, Map<String, Object> message) {
            this.lastRespondExecutionId = executionId;
            this.lastRespondMessage = message;
        }

        @Override
        public void cancel(String executionId, String reason) {
            this.lastCancelExecutionId = executionId;
            this.lastCancelReason = reason;
        }

        @Override
        public List<ConductorAgentSummary> listAgents() {
            return List.of();
        }
    }

    private static ConductorAgentExecution execution(ConductorAgentState state) {
        return ConductorAgentExecution.builder()
                .executionId("exec-1")
                .agentName("planner")
                .sessionId("sess-1")
                .state(state)
                .build();
    }

    private static TaskModel taskModel(Map<String, Object> input) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        model.setTaskId("conductor-task-1");
        model.setWorkflowInstanceId("wf-1");
        model.setReferenceTaskName("agent_ref");
        model.setIteration(0);
        return model;
    }

    private static Map<String, Object> conductorInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("agentName", "planner");
        input.put("text", "plan my trip");
        return input;
    }

    // 1. RUNNING -> task stays IN_PROGRESS, execution identity recorded.
    @Test
    void start_running_movesToInProgress() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals("exec-1", task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        assertEquals("planner", task.getOutputData().get(ConductorAgentResults.KEY_AGENT_NAME));
        assertEquals("RUNNING", task.getOutputData().get(ConductorAgentResults.KEY_STATE));
    }

    // 2. COMPLETED -> task COMPLETED, output merged verbatim under "output", text surfaced.
    @Test
    void start_completed_completesWithOutputAndText() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        ConductorAgentExecution ex = execution(ConductorAgentState.COMPLETED);
        ex.setOutput(Map.of("answer", 42));
        ex.setText("all done");
        runtime.startResult = ex;
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("answer", 42), task.getOutputData().get(ConductorAgentResults.KEY_OUTPUT));
        assertEquals("all done", task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
    }

    // 3. WAITING (human/tool) -> task COMPLETES, surfaces waiting flag + pendingTool.
    @Test
    void start_waiting_completesAndSurfacesPendingTool() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        ConductorAgentExecution ex = execution(ConductorAgentState.WAITING);
        ex.setPendingTool(Map.of("type", "human", "question", "Which city?"));
        ex.setText("Which city?");
        runtime.startResult = ex;
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Boolean.TRUE, task.getOutputData().get(ConductorAgentResults.KEY_WAITING));
        assertEquals(
                Map.of("type", "human", "question", "Which city?"),
                task.getOutputData().get(ConductorAgentResults.KEY_PENDING_TOOL));
        assertEquals("Which city?", task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
    }

    // 4. FAILED -> task FAILED with the runtime's reason.
    @Test
    void start_failed_failsWithReason() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        ConductorAgentExecution ex = execution(ConductorAgentState.FAILED);
        ex.setReasonForIncompletion("model error");
        runtime.startResult = ex;
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("model error", task.getReasonForIncompletion());
    }

    // 5. CANCELED -> task CANCELED.
    @Test
    void start_canceled_marksCanceled() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.CANCELED);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    // 6. Blank agentName on a fresh start -> terminal failure.
    @Test
    void start_blankAgentName_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.remove("agentName");
        TaskModel task = taskModel(input);
        delegate.start(task);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("AGENT (conductor) requires 'agentName'", task.getReasonForIncompletion());
        assertNull(runtime.lastStartRequest);
    }

    // 7. Blank prompt on a fresh start -> terminal failure.
    @Test
    void start_blankPrompt_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.remove("text");
        TaskModel task = taskModel(input);
        delegate.start(task);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "AGENT (conductor) requires 'text' or 'prompt'", task.getReasonForIncompletion());
        assertNull(runtime.lastStartRequest);
    }

    // 8. Absolute-deadline guard -> execute() fails terminally past maxDurationSeconds.
    @Test
    void execute_deadlineExceeded_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.statusResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.put("maxDurationSeconds", 1);
        TaskModel task = taskModel(input);
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        // Started well beyond the 1s deadline.
        task.addOutput(
                ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis() - 10_000L);

        boolean changed = delegate.execute(task);

        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("max duration"));
    }

    // 9. Poll-failure cap -> execute() fails terminally after maxPollFailures transient errors.
    @Test
    void execute_pollFailureCap_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.throwOnStatus = true;
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.put("maxPollFailures", 2);
        TaskModel task = taskModel(input);
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        // First failure -> transient, keep polling.
        boolean first = delegate.execute(task);
        assertFalse(first);
        assertEquals(
                1,
                ((Number) task.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());

        // Second failure -> hits the cap, terminal.
        boolean second = delegate.execute(task);
        assertTrue(second);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("consecutive poll failures"));
    }

    // 10. Deterministic idempotency key derived from durable task identity.
    @Test
    void start_buildsDeterministicIdempotencyKey() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        task.setIteration(3);
        delegate.start(task);

        assertEquals(
                "conductor-agent-wf-1:agent_ref:3",
                runtime.lastStartRequest.getIdempotencyKey());
        assertEquals("planner", runtime.lastStartRequest.getAgentName());
        assertEquals("plan my trip", runtime.lastStartRequest.getPrompt());
    }

    // 10b. Resume path: executionId present -> respond() is called with the prompt as result.
    @Test
    void start_withExecutionId_resumesViaRespond() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.statusResult = execution(ConductorAgentState.COMPLETED);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        input.put("text", "New York");
        TaskModel task = taskModel(input);
        delegate.start(task);

        assertEquals("exec-1", runtime.lastRespondExecutionId);
        assertEquals(Map.of("result", "New York"), runtime.lastRespondMessage);
        assertNull(runtime.lastStartRequest);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    // 11. Runtime absent -> terminal failure with the embedded-runtime message.
    @Test
    void start_runtimeAbsent_failsTerminally() {
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.empty());

        TaskModel task = taskModel(conductorInput());
        delegate.start(task);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)",
                task.getReasonForIncompletion());
    }
}
