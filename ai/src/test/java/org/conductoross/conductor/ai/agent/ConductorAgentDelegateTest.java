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

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.model.A2ACallRequest;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
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

    /**
     * Parses the request the way {@code AgentTask} does before dispatching, so these direct
     * delegate calls thread in the already-parsed request (the delegate no longer re-parses).
     */
    private static A2ACallRequest request(TaskModel task) {
        return new ObjectMapperProvider()
                .getObjectMapper()
                .convertValue(task.getInputData(), A2ACallRequest.class);
    }

    // 1. RUNNING -> task stays IN_PROGRESS, execution identity recorded.
    @Test
    void start_running_movesToInProgress() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        delegate.start(task, request(task));

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
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis() - 10_000L);

        boolean changed = delegate.execute(task, request(task));

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
        boolean first = delegate.execute(task, request(task));
        assertFalse(first);
        assertEquals(
                1,
                ((Number) task.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES))
                        .intValue());

        // Second failure -> hits the cap, terminal.
        boolean second = delegate.execute(task, request(task));
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
        delegate.start(task, request(task));

        assertEquals(
                "conductor-agent-wf-1:agent_ref:3", runtime.lastStartRequest.getIdempotencyKey());
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
        delegate.start(task, request(task));

        assertEquals("exec-1", runtime.lastRespondExecutionId);
        assertEquals(Map.of("result", "New York"), runtime.lastRespondMessage);
        assertNull(runtime.lastStartRequest);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    // 10c. Resume path with a blank prompt -> terminal failure, respond() never called.
    @Test
    void start_withExecutionId_blankPrompt_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.statusResult = execution(ConductorAgentState.COMPLETED);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("executionId", "exec-1");
        TaskModel task = taskModel(input);
        delegate.start(task, request(task));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "AGENT (conductor) requires 'text' or 'prompt'", task.getReasonForIncompletion());
        assertNull(runtime.lastRespondExecutionId);
        assertNull(runtime.lastRespondMessage);
    }

    // 10d. Non-retryable runtime error on fresh start (e.g. "Agent not found") -> terminal, not
    // retried. Demonstrates finding 1: today the delegate's generic catch treats this as retryable.
    @Test
    void start_nonRetryableRuntimeError_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startException = new NonRetryableAgentException("Agent not found: typo");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task, request(task));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("Agent not found: typo", task.getReasonForIncompletion());
    }

    // 10e. Non-retryable runtime error on resume (e.g. "No pending HUMAN task") -> terminal.
    @Test
    void start_resume_nonRetryableRuntimeError_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.respondException =
                new NonRetryableAgentException("No pending HUMAN task found for execution exec-1");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        input.put("text", "New York");
        TaskModel task = taskModel(input);
        delegate.start(task, request(task));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "No pending HUMAN task found for execution exec-1",
                task.getReasonForIncompletion());
    }

    // 10f. Non-retryable runtime error while polling in execute() -> terminal, not counted against
    // the transient poll-failure cap.
    @Test
    void execute_nonRetryableRuntimeError_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.statusException = new NonRetryableAgentException("Agent not found: typo");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        boolean changed = delegate.execute(task, request(task));

        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("Agent not found: typo", task.getReasonForIncompletion());
    }

    // 10g. Polling must not clobber start()'s recorded agentName/sessionId with nulls. The real
    // adapter's getStatus() does not repopulate identity fields, so a poll snapshot carries nulls
    // for them; the values captured at start() must survive.
    @Test
    void execute_nullIdentityFromPoll_preservesStartValues() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        // start() records agentName="planner"/sessionId="sess-1" from a fully-populated snapshot.
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        // A subsequent poll returns a terminal snapshot whose identity fields are null (mimicking
        // the real ConductorAgentRuntimeAdapter#getStatus()).
        runtime.statusResult =
                ConductorAgentExecution.builder()
                        .executionId("exec-1")
                        .agentName(null)
                        .sessionId(null)
                        .state(ConductorAgentState.COMPLETED)
                        .build();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(conductorInput());
        delegate.start(task, request(task));
        assertEquals("planner", task.getOutputData().get(ConductorAgentResults.KEY_AGENT_NAME));
        assertEquals("sess-1", task.getOutputData().get(ConductorAgentResults.KEY_SESSION_ID));

        boolean changed = delegate.execute(task, request(task));

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("exec-1", task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        assertEquals("planner", task.getOutputData().get(ConductorAgentResults.KEY_AGENT_NAME));
        assertEquals("sess-1", task.getOutputData().get(ConductorAgentResults.KEY_SESSION_ID));
    }

    // 11. Runtime absent -> terminal failure with the embedded-runtime message.
    @Test
    void start_runtimeAbsent_failsTerminally() {
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.empty());

        TaskModel task = taskModel(conductorInput());
        delegate.start(task, request(task));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)",
                task.getReasonForIncompletion());
    }

    // ---------------------------------------------------------------------------------------------
    // Prompt-resolution precedence (architecture.md §4.4): first non-blank of message-parts, then
    // parts, then text, then prompt. resolvePrompt() is private, so it is asserted through start()
    // via the prompt the delegate hands the runtime (lastStartRequest.getPrompt()).
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the fresh-start input, adding whatever prompt sources the case under test supplies.
     */
    private static Map<String, Object> promptInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("agentName", "planner");
        return input;
    }

    private static List<Map<String, Object>> textParts(String text) {
        return List.of(Map.of("kind", "text", "text", text));
    }

    private static String startedPrompt(Map<String, Object> input) {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));
        TaskModel task = taskModel(input);
        delegate.start(task, request(task));
        return runtime.lastStartRequest.getPrompt();
    }

    // 12a. message-parts win over parts, text, and prompt.
    @Test
    void resolvePrompt_messageWinsOverEverything() {
        Map<String, Object> input = promptInput();
        input.put("message", Map.of("parts", textParts("from-message")));
        input.put("parts", textParts("from-parts"));
        input.put("text", "from-text");
        input.put("prompt", "from-prompt");

        assertEquals("from-message", startedPrompt(input));
    }

    // 12b. parts win over text and prompt when there is no message.
    @Test
    void resolvePrompt_partsWinOverTextAndPrompt() {
        Map<String, Object> input = promptInput();
        input.put("parts", textParts("from-parts"));
        input.put("text", "from-text");
        input.put("prompt", "from-prompt");

        assertEquals("from-parts", startedPrompt(input));
    }

    // 12c. text wins over prompt when there is no message/parts.
    @Test
    void resolvePrompt_textWinsOverPrompt() {
        Map<String, Object> input = promptInput();
        input.put("text", "from-text");
        input.put("prompt", "from-prompt");

        assertEquals("from-text", startedPrompt(input));
    }

    // 12d. prompt is the last resort.
    @Test
    void resolvePrompt_promptIsLastResort() {
        Map<String, Object> input = promptInput();
        input.put("prompt", "from-prompt");

        assertEquals("from-prompt", startedPrompt(input));
    }

    // 12e. All prompt sources blank/absent -> fresh start fails terminally, runtime never called.
    @Test
    void resolvePrompt_allBlank_failsTerminally() {
        FakeConductorAgentRuntime runtime = new FakeConductorAgentRuntime();
        runtime.startResult = execution(ConductorAgentState.RUNNING);
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(Optional.of(runtime));

        TaskModel task = taskModel(promptInput());
        delegate.start(task, request(task));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "AGENT (conductor) requires 'text' or 'prompt'", task.getReasonForIncompletion());
        assertNull(runtime.lastStartRequest);
    }

    // ---------------------------------------------------------------------------------------------
    // Dispatch predicates (architecture.md §4.1). These are the keys AgentTask branches on; assert
    // the shared A2AService contract directly so the dispatch can't silently drift.
    // ---------------------------------------------------------------------------------------------

    // 13. isConductorAgentType is true only for "conductor" (case-insensitive).
    @Test
    void isConductorAgentType_contract() {
        assertTrue(A2AService.isConductorAgentType("conductor"));
        assertTrue(A2AService.isConductorAgentType("CONDUCTOR"));
        assertFalse(A2AService.isConductorAgentType(null));
        assertFalse(A2AService.isConductorAgentType(""));
        assertFalse(A2AService.isConductorAgentType("  "));
        assertFalse(A2AService.isConductorAgentType("a2a"));
    }

    // 14. isA2aAgentType defaults null/blank to a2a, and matches "a2a" case-insensitively.
    @Test
    void isA2aAgentType_contract() {
        assertTrue(A2AService.isA2aAgentType(null));
        assertTrue(A2AService.isA2aAgentType(""));
        assertTrue(A2AService.isA2aAgentType("  "));
        assertTrue(A2AService.isA2aAgentType("a2a"));
        assertTrue(A2AService.isA2aAgentType("A2A"));
        assertFalse(A2AService.isA2aAgentType("conductor"));
    }
}
