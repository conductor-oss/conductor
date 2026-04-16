/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.execution.tasks;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PullWorkflowMessagesTest {

    private static final String WORKFLOW_ID = "test-workflow-id";

    private WorkflowMessageQueueDAO dao;
    private WorkflowMessageQueueProperties properties;
    private WorkflowExecutor executor;
    private PullWorkflowMessages task;
    private WorkflowModel workflow;

    @Before
    public void setUp() {
        dao = mock(WorkflowMessageQueueDAO.class);
        properties = new WorkflowMessageQueueProperties();
        executor = mock(WorkflowExecutor.class);
        task = new PullWorkflowMessages(dao, properties);

        workflow = new WorkflowModel();
        workflow.setWorkflowId(WORKFLOW_ID);
    }

    // --- non-blocking: empty queue ---

    @Test
    public void nonBlocking_emptyQueue_completesWithEmptyOutput() {
        when(dao.pop(WORKFLOW_ID, 1)).thenReturn(Collections.emptyList());

        TaskModel taskModel = taskWithInput(Map.of(PullWorkflowMessages.INPUT_BLOCKING, false));
        boolean progressed = task.execute(workflow, taskModel, executor);

        assertTrue(progressed);
        assertEquals(TaskModel.Status.COMPLETED, taskModel.getStatus());
        assertEquals(
                Collections.emptyList(),
                taskModel.getOutputData().get(PullWorkflowMessages.OUTPUT_MESSAGES));
        assertEquals(0, taskModel.getOutputData().get(PullWorkflowMessages.OUTPUT_COUNT));
    }

    @Test
    public void nonBlocking_withMessages_completesWithMessages() {
        WorkflowMessage msg =
                new WorkflowMessage(
                        "msg-id", WORKFLOW_ID, Map.of("key", "value"), "2024-01-01T00:00:00Z");
        when(dao.pop(WORKFLOW_ID, 1)).thenReturn(List.of(msg));

        TaskModel taskModel = taskWithInput(Map.of(PullWorkflowMessages.INPUT_BLOCKING, false));
        boolean progressed = task.execute(workflow, taskModel, executor);

        assertTrue(progressed);
        assertEquals(TaskModel.Status.COMPLETED, taskModel.getStatus());
        assertEquals(
                List.of(msg), taskModel.getOutputData().get(PullWorkflowMessages.OUTPUT_MESSAGES));
        assertEquals(1, taskModel.getOutputData().get(PullWorkflowMessages.OUTPUT_COUNT));
    }

    // --- blocking (default): empty queue ---

    @Test
    public void blocking_emptyQueue_staysInProgress() {
        when(dao.pop(WORKFLOW_ID, 1)).thenReturn(Collections.emptyList());

        TaskModel taskModel = taskWithInput(Map.of(PullWorkflowMessages.INPUT_BLOCKING, true));
        boolean progressed = task.execute(workflow, taskModel, executor);

        assertFalse(progressed);
        assertNotEquals(TaskModel.Status.COMPLETED, taskModel.getStatus());
    }

    @Test
    public void defaultBehavior_emptyQueue_staysInProgress() {
        when(dao.pop(WORKFLOW_ID, 1)).thenReturn(Collections.emptyList());

        // No INPUT_BLOCKING key — should default to blocking
        TaskModel taskModel = taskWithInput(Map.of());
        boolean progressed = task.execute(workflow, taskModel, executor);

        assertFalse(progressed);
        assertNotEquals(TaskModel.Status.COMPLETED, taskModel.getStatus());
    }

    // --- helpers ---

    private TaskModel taskWithInput(Map<String, Object> input) {
        TaskModel taskModel = new TaskModel();
        taskModel.getInputData().putAll(input);
        return taskModel;
    }
}
