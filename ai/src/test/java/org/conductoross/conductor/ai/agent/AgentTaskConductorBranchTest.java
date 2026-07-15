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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Dispatch coverage for {@link AgentTask}'s {@code agentType} branch selection — the
 * conductor-branch analogue of {@code AgentTaskTest}. Proves that {@code agentType: "conductor"}
 * routes start/execute/cancel to {@link ConductorAgentDelegate} (observed through a scriptable
 * {@link FakeWorkflowExecutor}) while {@code "a2a"}/blank/null do not, and that {@code
 * getEvaluationOffset} uses the poll cadence on the conductor branch (test-plan.md §4.3).
 */
class AgentTaskConductorBranchTest {

    private FakeWorkflowExecutor executor;
    private AgentTask agentTask;

    @BeforeEach
    void setUp() {
        executor = new FakeWorkflowExecutor();
        agentTask = new AgentTask(new A2AService(new OkHttpClient()), new StandardEnvironment());
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

    /** A non-conductor input: distinct agentType, no agentUrl (so the a2a branch fails fast). */
    private static Map<String, Object> nonConductorInput(String agentType) {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", agentType); // may be null; a2a is the null/blank default
        input.put("text", "plan my trip");
        return input;
    }

    /**
     * Structurally-invalid input: {@code agentVersion} is an Integer field, so the string "abc"
     * cannot be coerced and {@code objectMapper.convertValue} throws IllegalArgumentException.
     * Before the fix this escaped every system-task entry point; the entry points must now fail the
     * task cleanly instead.
     */
    private static Map<String, Object> malformedInput() {
        Map<String, Object> input = conductorInput();
        input.put("agentVersion", "abc"); // not an Integer -> convertValue throws
        return input;
    }

    // start: malformed input must not escape the entry point; the task fails terminally
    // (permanent).
    @Test
    void start_malformedInput_failsTerminallyWithoutThrowing() {
        TaskModel model = taskModel(malformedInput());

        agentTask.start(null, model, null); // must not throw

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertNull(runtime.lastStartRequest, "malformed input must not reach the delegate");
    }

    // execute: malformed input must not escape the entry point; the task fails terminally.
    @Test
    void execute_malformedInput_failsTerminallyWithoutThrowing() {
        TaskModel model = taskModel(malformedInput());

        boolean changed = agentTask.execute(null, model, null); // must not throw

        assertEquals(true, changed);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }

    // getEvaluationOffset: the engine's cadence hook must never throw; fall back to the default.
    @Test
    void getEvaluationOffset_malformedInput_returnsDefaultWithoutThrowing() {
        assertEquals(
                Optional.of(5L), agentTask.getEvaluationOffset(taskModel(malformedInput()), 30));
    }

    // cancel: must not throw on malformed input and must still mark the task CANCELED.
    @Test
    void cancel_malformedInput_marksCanceledWithoutThrowing() {
        TaskModel model = taskModel(malformedInput());

        agentTask.cancel(null, model, null); // must not throw

        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
        assertNull(runtime.lastCancelExecutionId, "malformed input must not reach the delegate");
    }

    // start: conductor -> delegate.start (the fake records the start request).
    @Test
    void start_conductor_routesToDelegate() {
        agentTask.start(null, taskModel(conductorInput()), executor);

        assertEquals("planner", executor.lastStartRequest.getName());
        assertEquals("plan my trip", executor.lastStartRequest.getPrompt());
    }

    // start: a2a / blank / null never reach the conductor delegate.
    @Test
    void start_nonConductor_doesNotRouteToDelegate() {
        agentTask.start(null, taskModel(nonConductorInput("a2a")), executor);
        assertNull(executor.lastStartRequest, "explicit a2a must not hit the conductor delegate");

        agentTask.start(null, taskModel(nonConductorInput("  ")), executor);
        assertNull(
                executor.lastStartRequest, "blank agentType must not hit the conductor delegate");

        agentTask.start(null, taskModel(nonConductorInput(null)), executor);
        assertNull(executor.lastStartRequest, "null agentType must not hit the conductor delegate");
    }

    // execute: conductor -> delegate.execute (the fake's snapshot settles the task).
    @Test
    void execute_conductor_routesToDelegate() {
        executor.workflow = workflow(WorkflowModel.Status.COMPLETED);

        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        boolean changed = agentTask.execute(null, model, executor);

        assertEquals(true, changed);
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        // Only the conductor delegate writes KEY_STATE from a ConductorAgentExecution snapshot.
        assertEquals("COMPLETED", model.getOutputData().get(ConductorAgentResults.KEY_STATE));
    }

    // Each execute() poll increments pollCount (AGENT is sync; the async executor doesn't do it).
    @Test
    void execute_incrementsPollCountEachPoll() {
        executor.workflow = workflow(WorkflowModel.Status.RUNNING); // stays IN_PROGRESS
        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        agentTask.execute(null, model, executor);
        agentTask.execute(null, model, executor);
        agentTask.execute(null, model, executor);

        assertEquals(3, model.getPollCount());
    }

    // cancel: conductor -> delegate.cancel (the fake records the execution id).
    @Test
    void cancel_conductor_routesToDelegate() {
        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        agentTask.cancel(null, model, executor);

        assertEquals("exec-1", executor.lastTerminatedExecutionId);
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    // cancel-on-timeout: the child is terminated but the TIMED_OUT status is preserved (cleanup
    // hook, not a status transition).
    @Test
    void cancel_conductor_preservesTimedOutStatusAndTerminatesChild() {
        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.setStatus(TaskModel.Status.TIMED_OUT);

        agentTask.cancel(null, model, executor);

        assertEquals("exec-1", executor.lastTerminatedExecutionId);
        assertEquals(TaskModel.Status.TIMED_OUT, model.getStatus());
    }

    // cancel: a2a never reaches the conductor delegate.
    @Test
    void cancel_nonConductor_doesNotRouteToDelegate() {
        TaskModel model = taskModel(nonConductorInput("a2a"));
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        agentTask.cancel(null, model, executor);

        assertNull(executor.lastTerminatedExecutionId);
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    // getEvaluationOffset: conductor branch re-polls at pollIntervalSeconds (default 5), no
    // backstop.
    @Test
    void getEvaluationOffset_conductor_usesPollCadence() {
        assertEquals(
                Optional.of(5L), agentTask.getEvaluationOffset(taskModel(conductorInput()), 30));

        Map<String, Object> custom = conductorInput();
        custom.put("pollIntervalSeconds", 12);
        assertEquals(Optional.of(12L), agentTask.getEvaluationOffset(taskModel(custom), 30));

        // Sanity: the custom cadence is genuinely distinct from the default.
        assertNotEquals(
                agentTask.getEvaluationOffset(taskModel(conductorInput()), 30),
                agentTask.getEvaluationOffset(taskModel(custom), 30));
    }
}
