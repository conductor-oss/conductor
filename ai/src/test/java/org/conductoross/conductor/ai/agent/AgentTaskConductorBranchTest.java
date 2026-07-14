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

import com.netflix.conductor.model.TaskModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Dispatch coverage for {@link AgentTask}'s {@code agentType} branch selection — the
 * conductor-branch analogue of {@code AgentTaskTest}. Proves that {@code agentType: "conductor"}
 * routes start/execute/cancel to {@link ConductorAgentDelegate} (observed through {@link
 * FakeConductorAgentRuntime}'s recorded calls) while {@code "a2a"}/blank/null do not, and that
 * {@code getEvaluationOffset} uses the poll cadence on the conductor branch (test-plan.md §4.3).
 */
class AgentTaskConductorBranchTest {

    private FakeConductorAgentRuntime runtime;
    private AgentTask agentTask;

    @BeforeEach
    void setUp() {
        runtime = new FakeConductorAgentRuntime();
        agentTask =
                new AgentTask(
                        new A2AService(new OkHttpClient()),
                        new StandardEnvironment(),
                        Optional.of(runtime));
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

    /** A non-conductor input: distinct agentType, no agentUrl (so the a2a branch fails fast). */
    private static Map<String, Object> nonConductorInput(String agentType) {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", agentType); // may be null; a2a is the null/blank default
        input.put("text", "plan my trip");
        return input;
    }

    // start: conductor -> delegate.start (the fake records the start request).
    @Test
    void start_conductor_routesToDelegate() {
        runtime.startResult = execution(ConductorAgentState.RUNNING);

        agentTask.start(null, taskModel(conductorInput()), null);

        assertEquals("planner", runtime.lastStartRequest.getAgentName());
        assertEquals("plan my trip", runtime.lastStartRequest.getPrompt());
    }

    // start: a2a / blank / null never reach the conductor delegate.
    @Test
    void start_nonConductor_doesNotRouteToDelegate() {
        runtime.startResult = execution(ConductorAgentState.RUNNING);

        agentTask.start(null, taskModel(nonConductorInput("a2a")), null);
        assertNull(runtime.lastStartRequest, "explicit a2a must not hit the conductor delegate");

        agentTask.start(null, taskModel(nonConductorInput("  ")), null);
        assertNull(runtime.lastStartRequest, "blank agentType must not hit the conductor delegate");

        agentTask.start(null, taskModel(nonConductorInput(null)), null);
        assertNull(runtime.lastStartRequest, "null agentType must not hit the conductor delegate");
    }

    // execute: conductor -> delegate.execute (the fake's snapshot settles the task).
    @Test
    void execute_conductor_routesToDelegate() {
        runtime.statusResult = execution(ConductorAgentState.COMPLETED);

        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");
        model.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());

        boolean changed = agentTask.execute(null, model, null);

        assertEquals(true, changed);
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        // Only the conductor delegate writes KEY_STATE from a ConductorAgentExecution snapshot.
        assertEquals("COMPLETED", model.getOutputData().get(ConductorAgentResults.KEY_STATE));
    }

    // cancel: conductor -> delegate.cancel (the fake records the execution id).
    @Test
    void cancel_conductor_routesToDelegate() {
        TaskModel model = taskModel(conductorInput());
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        agentTask.cancel(null, model, null);

        assertEquals("exec-1", runtime.lastCancelExecutionId);
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    // cancel: a2a never reaches the conductor delegate.
    @Test
    void cancel_nonConductor_doesNotRouteToDelegate() {
        TaskModel model = taskModel(nonConductorInput("a2a"));
        model.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, "exec-1");

        agentTask.cancel(null, model, null);

        assertNull(runtime.lastCancelExecutionId);
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
