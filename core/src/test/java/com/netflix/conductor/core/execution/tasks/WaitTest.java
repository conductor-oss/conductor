/*
 * Copyright 2022 Conductor Authors.
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

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class WaitTest {

    @Test
    public void signalWaitDoesNothingWhenWorkflowTimeoutNotElapsed() {
        Wait wait = new Wait();
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setTimeoutSeconds(60);
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setWaitTimeout(0);

        boolean result = wait.execute(workflow, task, mock(WorkflowExecutor.class));

        assertFalse(result);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    public void signalWaitCancelsItselfWhenWorkflowTimeoutElapsed() {
        Wait wait = new Wait();
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setTimeoutSeconds(1);
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis() - 5_000L);

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setWaitTimeout(0);

        boolean result = wait.execute(workflow, task, mock(WorkflowExecutor.class));

        assertTrue(result);
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    @Test
    public void signalWaitWithoutWorkflowTimeoutStaysInProgress() {
        Wait wait = new Wait();
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis() - 1_000_000L);

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setWaitTimeout(0);

        boolean result = wait.execute(workflow, task, mock(WorkflowExecutor.class));

        assertFalse(result);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }
}
