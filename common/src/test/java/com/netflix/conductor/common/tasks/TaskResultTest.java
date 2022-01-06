/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.tasks;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import static org.junit.Assert.assertEquals;

public class TaskResultTest {

    private Task task;
    private TaskResult taskResult;

    @Before
    public void setUp() {
        task = new Task();
        task.setWorkflowInstanceId("workflow-id");
        task.setTaskId("task-id");
        task.setReasonForIncompletion("reason");
        task.setCallbackAfterSeconds(10);
        task.setWorkerId("worker-id");
        task.setOutputData(new HashMap<>());
        task.setExternalOutputPayloadStoragePath("externalOutput");
    }

    @Test
    public void testCanceledTask() {
        task.setStatus(Task.Status.CANCELED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.FAILED, taskResult.getStatus());
    }

    @Test
    public void testCompletedWithErrorsTask() {
        task.setStatus(Task.Status.COMPLETED_WITH_ERRORS);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.FAILED, taskResult.getStatus());
    }

    @Test
    public void testScheduledTask() {
        task.setStatus(Task.Status.SCHEDULED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.IN_PROGRESS, taskResult.getStatus());
    }

    @Test
    public void testCompltetedTask() {
        task.setStatus(Task.Status.COMPLETED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.COMPLETED, taskResult.getStatus());
    }

    private void validateTaskResult() {
        assertEquals(task.getWorkflowInstanceId(), taskResult.getWorkflowInstanceId());
        assertEquals(task.getTaskId(), taskResult.getTaskId());
        assertEquals(task.getReasonForIncompletion(), taskResult.getReasonForIncompletion());
        assertEquals(task.getCallbackAfterSeconds(), taskResult.getCallbackAfterSeconds());
        assertEquals(task.getWorkerId(), taskResult.getWorkerId());
        assertEquals(task.getOutputData(), taskResult.getOutputData());
        assertEquals(
                task.getExternalOutputPayloadStoragePath(),
                taskResult.getExternalOutputPayloadStoragePath());
    }
}
