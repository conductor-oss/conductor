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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end lifecycle test for the {@code AGENT} (conductor) branch, driven through the real {@link
 * AgentTask} system-task entry points against an in-process {@link FakeConductorAgentRuntime} — the
 * conductor-branch analogue of {@code A2AEndToEndTest} (no mock frameworks, no socket).
 *
 * <p>Covers start&rarr;poll&rarr;complete, plus the {@code WAITING}&rarr;resume flow, per test-plan.md
 * §4.1.
 */
class ConductorAgentEndToEndTest {

    private FakeConductorAgentRuntime runtime;
    private AgentTask agentTask;

    @BeforeEach
    void setUp() {
        runtime = new FakeConductorAgentRuntime();
        // A real A2AService/Environment: the conductor branch never touches either (it dispatches
        // on the static agentType predicate and delegates to the injected runtime), so no HTTP or
        // property lookup happens on this path.
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

    // Full lifecycle: RUNNING on start -> IN_PROGRESS, then a COMPLETED poll -> COMPLETED.
    @Test
    void lifecycle_runningThenCompleted() {
        runtime.startResult = execution(ConductorAgentState.RUNNING);

        TaskModel task = taskModel(conductorInput());
        agentTask.start(null, task, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals("exec-1", task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        assertEquals("planner", task.getOutputData().get(ConductorAgentResults.KEY_AGENT_NAME));
        assertEquals("sess-1", task.getOutputData().get(ConductorAgentResults.KEY_SESSION_ID));
        assertEquals("RUNNING", task.getOutputData().get(ConductorAgentResults.KEY_STATE));
        Object startedAt = task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT);
        assertNotNull(startedAt, "agentStartedAt must be anchored on start");

        // Advance the run and poll: execute() settles the task.
        ConductorAgentExecution completed = execution(ConductorAgentState.COMPLETED);
        completed.setOutput(Map.of("answer", 42));
        completed.setText("all done");
        runtime.statusResult = completed;

        boolean changed = agentTask.execute(null, task, null);

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("answer", 42), task.getOutputData().get(ConductorAgentResults.KEY_OUTPUT));
        assertEquals("all done", task.getOutputData().get(ConductorAgentResults.KEY_TEXT));
        // agentStartedAt is anchored once and never rewritten by execute().
        assertEquals(startedAt, task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT));
    }

    // WAITING on start -> task COMPLETES surfacing the pending request; a second AGENT call carrying
    // the executionId resumes via respond() and routes the resumed run to COMPLETED.
    @Test
    void waiting_thenResumeRoutesToCompleted() {
        ConductorAgentExecution waiting = execution(ConductorAgentState.WAITING);
        waiting.setPendingTool(Map.of("type", "human", "question", "Which city?"));
        waiting.setText("Which city?");
        runtime.startResult = waiting;

        TaskModel first = taskModel(conductorInput());
        agentTask.start(null, first, null);

        assertEquals(TaskModel.Status.COMPLETED, first.getStatus());
        assertEquals(Boolean.TRUE, first.getOutputData().get(ConductorAgentResults.KEY_WAITING));
        assertEquals(
                Map.of("type", "human", "question", "Which city?"),
                first.getOutputData().get(ConductorAgentResults.KEY_PENDING_TOOL));
        assertEquals("WAITING", first.getOutputData().get(ConductorAgentResults.KEY_STATE));

        // Resume: a fresh AGENT call carrying the executionId + the human's answer.
        runtime.statusResult = execution(ConductorAgentState.COMPLETED);
        Map<String, Object> resumeInput = new HashMap<>();
        resumeInput.put("agentType", "conductor");
        resumeInput.put("executionId", "exec-1");
        resumeInput.put("text", "New York");
        TaskModel second = taskModel(resumeInput);

        agentTask.start(null, second, null);

        assertEquals("exec-1", runtime.lastRespondExecutionId);
        assertEquals(Map.of("result", "New York"), runtime.lastRespondMessage);
        assertEquals(TaskModel.Status.COMPLETED, second.getStatus());
    }

    // The conductor branch re-polls at the configured cadence (default 5s), with no push backstop.
    @Test
    void getEvaluationOffset_usesPollCadence() {
        assertEquals(
                Optional.of(5L),
                agentTask.getEvaluationOffset(taskModel(conductorInput()), 30));

        Map<String, Object> input = conductorInput();
        input.put("pollIntervalSeconds", 9);
        assertEquals(Optional.of(9L), agentTask.getEvaluationOffset(taskModel(input), 30));
    }
}
