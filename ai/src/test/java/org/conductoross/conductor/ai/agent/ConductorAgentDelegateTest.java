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
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit coverage for the WorkflowExecutor-backed conductor-agent state machine. */
class ConductorAgentDelegateTest {

    private final ConductorAgentDelegate delegate = new ConductorAgentDelegate();

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
        input.put("name", "planner");
        input.put("prompt", "plan my trip");
        return input;
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

    private static TaskModel waitingHumanTask() {
        TaskModel human = new TaskModel();
        human.setTaskId("human-1");
        human.setTaskType("HUMAN");
        human.setReferenceTaskName("approval");
        human.setStatus(TaskModel.Status.IN_PROGRESS);
        human.setInputData(
                Map.of(
                        "tool_calls",
                        List.of(
                                Map.of(
                                        "name",
                                        "book_trip",
                                        "inputParameters",
                                        Map.of("city", "Paris"))),
                        "response_schema",
                        Map.of("type", "object")));
        return human;
    }

    @Test
    void start_startsRegisteredAgentAndMovesToInProgress() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        TaskModel task = taskModel(conductorInput());
        task.setIteration(3);

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals("exec-1", task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        assertEquals("planner", executor.lastStartRequest.getName());
        assertEquals("plan my trip", executor.lastStartRequest.getPrompt());
        assertEquals(
                "conductor-agent-wf-1:agent_ref:3", executor.lastStartRequest.getIdempotencyKey());
    }

    @Test
    void start_blankNameFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        Map<String, Object> input = conductorInput();
        input.remove("name");
        TaskModel task = taskModel(input);

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertNull(executor.lastStartRequest);
    }

    @Test
    void start_blankPromptFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        Map<String, Object> input = conductorInput();
        input.remove("prompt");
        TaskModel task = taskModel(input);

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertNull(executor.lastStartRequest);
    }

    @Test
    void execute_completedWorkflowCopiesOutputAndText() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.COMPLETED);
        executor.workflow.setOutput(Map.of("answer", 42, "text", "all done"));
        TaskModel task = pollingTask();

        boolean changed = delegate.execute(task, executor);

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("answer", 42, "text", "all done"),
                task.getOutputData().get(ConductorAgentResults.KEY_OUTPUT));
        assertEquals("all done", task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
    }

    @Test
    void execute_waitingWorkflowSurfacesPendingHumanRequest() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.RUNNING);
        executor.workflow.setTasks(List.of(waitingHumanTask()));
        TaskModel task = pollingTask();

        boolean changed = delegate.execute(task, executor);

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Boolean.TRUE, task.getOutputData().get(ConductorAgentResults.KEY_WAITING));
        assertEquals(
                List.of(Map.of("name", "book_trip", "args", Map.of("city", "Paris"))),
                ((Map<?, ?>) task.getOutputData().get(ConductorAgentResults.KEY_PENDING_TOOL))
                        .get("toolCalls"));
    }

    @Test
    void execute_failedAndTerminatedWorkflowsMapToTaskStates() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.FAILED);
        executor.workflow.setReasonForIncompletion("model error");
        TaskModel failed = pollingTask();

        assertTrue(delegate.execute(failed, executor));
        assertEquals(TaskModel.Status.FAILED, failed.getStatus());
        assertEquals("model error", failed.getReasonForIncompletion());

        executor.workflow = workflow(WorkflowModel.Status.TERMINATED);
        TaskModel terminated = pollingTask();
        assertTrue(delegate.execute(terminated, executor));
        assertEquals(TaskModel.Status.CANCELED, terminated.getStatus());
    }

    @Test
    void execute_deadlineExceededFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        Map<String, Object> input = conductorInput();
        input.put("maxDurationSeconds", 1);
        TaskModel task = taskModel(input);
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis() - 10_000L);

        assertTrue(delegate.execute(task, executor));
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "exec-1",
                executor.lastTerminatedExecutionId,
                "the abandoned child execution must be terminated, not left running");
    }

    @Test
    void execute_pollFailureCapFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.throwOnGetWorkflow = true;
        Map<String, Object> input = conductorInput();
        input.put("maxPollFailures", 2);
        TaskModel task = taskModel(input);
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        assertFalse(delegate.execute(task, executor));
        assertTrue(delegate.execute(task, executor));
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "exec-1",
                executor.lastTerminatedExecutionId,
                "an unreachable child execution must still get a best-effort terminate call");
    }

    @Test
    void start_withExecutionIdCompletesPendingHumanTaskThenReadsStatus() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.RUNNING);
        executor.workflow.setTasks(List.of(waitingHumanTask()));
        executor.workflowAfterUpdate = workflow(WorkflowModel.Status.COMPLETED);
        executor.workflowAfterUpdate.setOutput(Map.of("answer", "New York"));
        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        input.put("prompt", "New York");
        TaskModel task = taskModel(input);

        delegate.start(task, executor);

        assertEquals("human-1", executor.lastTaskResult.getTaskId());
        assertEquals(Map.of("result", "New York"), executor.lastTaskResult.getOutputData());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull(executor.lastStartRequest);
    }

    @Test
    void cancelTerminatesChildWorkflowBestEffort() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        TaskModel task = pollingTask();

        delegate.cancel(task, executor, "workflow canceled");

        assertEquals("exec-1", executor.lastTerminatedExecutionId);
        assertEquals("workflow canceled", executor.lastTerminationReason);
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());

        executor.throwOnTerminate = true;
        delegate.cancel(task, executor, "again");
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    // A child that already finished on its own (e.g. its own workflow timeout -> TIMED_OUT) must
    // not be re-terminated by the cleanup hook — that would rewrite TIMED_OUT to TERMINATED.
    @Test
    void cancelSkipsTerminateWhenChildAlreadyTerminal() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.TIMED_OUT);
        TaskModel task = pollingTask();

        delegate.cancel(task, executor, "workflow canceled");

        assertNull(
                executor.lastTerminatedExecutionId,
                "an already-terminal child execution must be left untouched");
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    @Test
    void nullExecutorFailsTerminally() {
        TaskModel task = taskModel(conductorInput());

        delegate.start(task, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "Conductor agent execution requires a WorkflowExecutor",
                task.getReasonForIncompletion());
    }

    @Test
    void agentTypePredicatesRemainStable() {
        assertTrue(A2AService.isConductorAgentType("conductor"));
        assertTrue(A2AService.isConductorAgentType("CONDUCTOR"));
        assertFalse(A2AService.isConductorAgentType(null));
        assertTrue(A2AService.isA2aAgentType(null));
        assertTrue(A2AService.isA2aAgentType("a2a"));
        assertFalse(A2AService.isA2aAgentType("conductor"));
    }

    // ── Review-fix regression coverage ─────────────────────────────────────────────

    // Resume with a blank prompt must fail terminally before respond() is attempted — a null
    // prompt would otherwise NPE inside Map.of and be misclassified as a retryable failure.
    @Test
    void start_withExecutionIdBlankPromptFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("executionId", "exec-1");
        TaskModel task = taskModel(input);

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("AGENT (conductor) requires 'prompt'", task.getReasonForIncompletion());
        assertNull(executor.lastTaskResult, "respond() must never be reached");
    }

    // An unknown agent (NotFoundException from startAgentExecution) is permanent, not retried.
    @Test
    void start_unknownAgentFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.startException = new NotFoundException("Agent not found: typo");
        TaskModel task = taskModel(conductorInput());

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("Agent not found: typo", task.getReasonForIncompletion());
    }

    // Resuming an execution with nothing pending is permanent, not retried.
    @Test
    void start_resumeWithNothingPendingFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.RUNNING); // no pending wait task
        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        TaskModel task = taskModel(input);

        delegate.start(task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("No pending"));
    }

    // A purged/unknown execution during polling is permanent — don't burn the poll-failure budget.
    @Test
    void execute_missingExecutionFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.getWorkflowException =
                new NotFoundException("No such workflow found by id: exec-1");
        TaskModel task = pollingTask();

        boolean changed = delegate.execute(task, executor);

        assertTrue(changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
    }

    // The agent compiler emits the canonical "result" output key — text must surface from it.
    @Test
    void execute_completedTextFallsBackToResultKey() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.COMPLETED);
        executor.workflow.setOutput(Map.of("result", "The weather is sunny."));
        TaskModel task = pollingTask();

        boolean changed = delegate.execute(task, executor);

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                "The weather is sunny.", task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
    }

    // Schema-typed (Map) results stay in output only — no bogus text.
    @Test
    void execute_completedStructuredResultLeavesTextUnset() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        executor.workflow = workflow(WorkflowModel.Status.COMPLETED);
        executor.workflow.setOutput(Map.of("result", Map.of("score", 7)));
        TaskModel task = pollingTask();

        delegate.execute(task, executor);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull(task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
    }

    // Resuming a PULL_WORKFLOW_MESSAGES wait delivers through the workflow message queue and
    // re-decides — it must not force-complete the task via updateTask.
    @Test
    void start_resumePullWaitDeliversViaMessageQueue() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        WorkflowModel waiting = workflow(WorkflowModel.Status.RUNNING);
        waiting.getTasks().add(waitingPullTask());
        executor.workflow = waiting;

        RecordingMessageQueue queue = new RecordingMessageQueue();
        ConductorAgentDelegate wmqDelegate = new ConductorAgentDelegate(Optional.of(queue));

        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        TaskModel task = taskModel(input);

        wmqDelegate.start(task, executor);

        assertEquals("exec-1", queue.lastWorkflowId);
        assertEquals(Map.of("result", "plan my trip"), queue.lastMessage.getPayload());
        assertEquals("exec-1", executor.lastDecidedWorkflowId);
        assertNull(executor.lastTaskResult, "a pull wait must not be completed via updateTask");
        // The re-read snapshot still shows the wait -> the task completes with waiting=true.
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Boolean.TRUE, task.getOutputData().get(ConductorAgentResults.KEY_WAITING));
    }

    // Without the message queue, a pull wait fails terminally with a pointer to the queue API.
    @Test
    void start_resumePullWaitWithoutQueueFailsTerminally() {
        FakeWorkflowExecutor executor = new FakeWorkflowExecutor();
        WorkflowModel waiting = workflow(WorkflowModel.Status.RUNNING);
        waiting.getTasks().add(waitingPullTask());
        executor.workflow = waiting;

        Map<String, Object> input = conductorInput();
        input.put("executionId", "exec-1");
        TaskModel task = taskModel(input);

        delegate.start(task, executor); // default delegate: no queue configured

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("workflow message queue"));
    }

    private static TaskModel waitingPullTask() {
        TaskModel pull = new TaskModel();
        pull.setTaskId("pull-1");
        pull.setTaskType("PULL_WORKFLOW_MESSAGES");
        pull.setReferenceTaskName("wait_for_message");
        pull.setStatus(TaskModel.Status.IN_PROGRESS);
        return pull;
    }

    /** Hand-written recording queue — the conductor-agent analogue of FakeWorkflowExecutor. */
    private static final class RecordingMessageQueue implements WorkflowMessageQueueDAO {
        String lastWorkflowId;
        WorkflowMessage lastMessage;

        @Override
        public void push(String workflowId, WorkflowMessage message) {
            this.lastWorkflowId = workflowId;
            this.lastMessage = message;
        }

        @Override
        public List<WorkflowMessage> pop(String workflowId, int maxCount) {
            return List.of();
        }

        @Override
        public long size(String workflowId) {
            return 0;
        }

        @Override
        public void delete(String workflowId) {}
    }

    private static TaskModel pollingTask() {
        TaskModel task = taskModel(conductorInput());
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());
        return task;
    }
}
