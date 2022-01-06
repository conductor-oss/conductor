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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.google.protobuf.Any;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskTest {

    @Test
    public void test() {

        Task task = new Task();
        task.setStatus(Status.FAILED);
        assertEquals(Status.FAILED, task.getStatus());

        Set<String> resultStatues =
                Arrays.stream(TaskResult.Status.values())
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

    @Test
    public void testDeepCopyTask() {
        final Task task = new Task();
        // In order to avoid forgetting putting inside the copy method the newly added fields check
        // the number of declared fields.
        final int expectedTaskFieldsNumber = 40;
        final int declaredFieldsNumber = task.getClass().getDeclaredFields().length;

        assertEquals(expectedTaskFieldsNumber, declaredFieldsNumber);

        task.setCallbackAfterSeconds(111L);
        task.setCallbackFromWorker(false);
        task.setCorrelationId("correlation_id");
        task.setInputData(new HashMap<>());
        task.setOutputData(new HashMap<>());
        task.setReferenceTaskName("ref_task_name");
        task.setStartDelayInSeconds(1);
        task.setTaskDefName("task_def_name");
        task.setTaskType("dummy_task_type");
        task.setWorkflowInstanceId("workflowInstanceId");
        task.setWorkflowType("workflowType");
        task.setResponseTimeoutSeconds(11L);
        task.setStatus(Status.COMPLETED);
        task.setRetryCount(0);
        task.setPollCount(0);
        task.setTaskId("taskId");
        task.setWorkflowTask(new WorkflowTask());
        task.setDomain("domain");
        task.setInputMessage(Any.getDefaultInstance());
        task.setOutputMessage(Any.getDefaultInstance());
        task.setRateLimitPerFrequency(11);
        task.setRateLimitFrequencyInSeconds(11);
        task.setExternalInputPayloadStoragePath("externalInputPayloadStoragePath");
        task.setExternalOutputPayloadStoragePath("externalOutputPayloadStoragePath");
        task.setWorkflowPriority(0);
        task.setIteration(1);
        task.setExecutionNameSpace("name_space");
        task.setIsolationGroupId("groupId");
        task.setStartTime(12L);
        task.setEndTime(20L);
        task.setScheduledTime(7L);
        task.setRetried(false);
        task.setReasonForIncompletion("");
        task.setWorkerId("");
        task.setSubWorkflowId("");
        task.setSubworkflowChanged(false);

        final Task copy = task.deepCopy();
        assertEquals(task, copy);
    }
}
