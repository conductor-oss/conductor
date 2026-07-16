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

import org.conductoross.conductor.ai.a2a.A2AService;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
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

    private static TaskModel pollingTask() {
        TaskModel task = taskModel(conductorInput());
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());
        return task;
    }
}
