/*
 * Copyright 2016 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * @author Viren
 *
 */
public class TaskTest {

    @Test
    public void test() {

        Task task = new Task();
        task.setStatus(Status.FAILED);
        assertEquals(Status.FAILED, task.getStatus());

        Set<String> resultStatues = Arrays.stream(TaskResult.Status.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

        for (Status status : Status.values()) {
            if (resultStatues.contains(status.name())) {
                TaskResult.Status trStatus = TaskResult.Status.valueOf(status.name());
                assertEquals(status.name(), trStatus.name());

                task = new Task();
                task.setStatus(status);
                assertEquals(status, task.getStatus());

            }
        }
    }

    @Test
    public void testTaskDefinitionIfAvailable() {
        Task task = new Task();
        task.setStatus(Status.FAILED);
        assertEquals(Status.FAILED, task.getStatus());

        assertNull(task.getWorkflowTask());
        assertFalse(task.getTaskDefinition().isPresent());

        WorkflowTask workflowTask = new WorkflowTask();
        TaskDef taskDefinition = new TaskDef();
        workflowTask.setTaskDefinition(taskDefinition);
        task.setWorkflowTask(workflowTask);

        assertTrue(task.getTaskDefinition().isPresent());
        assertEquals(taskDefinition, task.getTaskDefinition().get());
    }

    @Test
    public void testTaskQueueWaitTime() {
        Task task = new Task();

        long currentTimeMillis = System.currentTimeMillis();
        task.setScheduledTime(currentTimeMillis - 30_000); // 30 seconds ago
        task.setStartTime(currentTimeMillis - 25_000);

        long queueWaitTime = task.getQueueWaitTime();
        assertEquals(5000L, queueWaitTime);

        task.setUpdateTime(currentTimeMillis - 20_000);
        task.setCallbackAfterSeconds(10);
        queueWaitTime = task.getQueueWaitTime();
        assertTrue(queueWaitTime > 0);
    }
}
