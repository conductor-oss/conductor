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
package org.conductoross.conductor.ai.a2a;

import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@link CancelAgentTask} — a plain, synchronous {@code WorkflowSystemTask}. */
class CancelAgentTaskTest {

    private A2AService a2aService;
    private WorkflowExecutor executor;
    private CancelAgentTask task;

    @BeforeEach
    void setUp() {
        a2aService = mock(A2AService.class);
        executor = mock(WorkflowExecutor.class);
        task = new CancelAgentTask(a2aService);
    }

    private static TaskModel taskModel(Map<String, Object> input) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        model.setTaskId("cancel-task-1");
        return model;
    }

    @Test
    void isAsync_isFalse() {
        assertFalse(task.isAsync(), "CANCEL_AGENT completes inline in start(), it never queues");
    }

    @Test
    void a2a_callsService() {
        A2ATask cancelled = new A2ATask();
        TaskStatus status = new TaskStatus();
        status.setState(TaskState.CANCELED);
        cancelled.setStatus(status);
        when(a2aService.cancelTask(eq("http://agent"), eq("t1"), any())).thenReturn(cancelled);

        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", "http://agent");
        input.put("taskId", "t1");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> taskOutput = (Map<String, Object>) model.getOutputData().get("task");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputStatus = (Map<String, Object>) taskOutput.get("status");
        assertEquals(TaskState.CANCELED, outputStatus.get("state"));
        verify(a2aService).cancelTask(eq("http://agent"), eq("t1"), any());
    }

    @Test
    void a2a_missingTaskId_isNonRetryable() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", "http://agent");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }

    @Test
    void conductor_terminatesExecution() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("executionId", "exec-1");
        input.put("reason", "user requested");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("exec-1", model.getOutputData().get("executionId"));
        assertEquals(Boolean.TRUE, model.getOutputData().get("canceled"));
        verify(executor).terminateWorkflow("exec-1", "user requested");
    }

    @Test
    void conductor_defaultsReasonWhenBlank() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        input.put("executionId", "exec-1");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        verify(executor).terminateWorkflow(eq("exec-1"), eq("Cancelled by CANCEL_AGENT task"));
    }

    @Test
    void conductor_missingExecutionId_isNonRetryable() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "conductor");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }

    @Test
    void unsupportedAgentType_isNonRetryable() {
        Map<String, Object> input = new HashMap<>();
        input.put("agentType", "langgraph");
        TaskModel model = taskModel(input);

        task.start(null, model, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }
}
