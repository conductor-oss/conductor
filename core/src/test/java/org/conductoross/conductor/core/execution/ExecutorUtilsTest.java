/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.core.execution;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public class ExecutorUtilsTest {

    @Test
    public void computePostponeUsesMinimumAcrossTasks() {
        TaskModel scheduledLong = newTask(TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.SCHEDULED);
        TaskDef longPollDef = new TaskDef();
        longPollDef.setPollTimeoutSeconds(50);
        WorkflowTask longWorkflowTask = new WorkflowTask();
        longWorkflowTask.setTaskDefinition(longPollDef);
        scheduledLong.setWorkflowTask(longWorkflowTask);

        TaskModel scheduledShort = newTask(TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.SCHEDULED);
        TaskDef shortPollDef = new TaskDef();
        shortPollDef.setPollTimeoutSeconds(5);
        WorkflowTask shortWorkflowTask = new WorkflowTask();
        shortWorkflowTask.setTaskDefinition(shortPollDef);
        scheduledShort.setWorkflowTask(shortWorkflowTask);

        WorkflowModel workflow =
                newWorkflow(Arrays.asList(scheduledLong, scheduledShort), 0 /* timeoutSeconds */);

        Duration result =
                ExecutorUtils.computePostpone(
                        workflow, Duration.ofSeconds(30), Duration.ofSeconds(3600));

        assertEquals(6, result.getSeconds());
    }

    @Test
    public void computePostponeCapsByMaxPostpone() {
        TaskModel responseTask = newTask(TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.IN_PROGRESS);
        responseTask.setResponseTimeoutSeconds(500);
        responseTask.setStartTime(System.currentTimeMillis());

        WorkflowModel workflow = newWorkflow(Arrays.asList(responseTask), 0);

        Duration result =
                ExecutorUtils.computePostpone(
                        workflow, Duration.ofSeconds(30), Duration.ofSeconds(60));

        assertEquals(60, result.getSeconds());
    }

    @Test
    public void computePostponeUsesScheduledPollTimeout() {
        TaskDef taskDef = new TaskDef();
        taskDef.setPollTimeoutSeconds(10);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel scheduled = newTask(TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.SCHEDULED);
        scheduled.setWorkflowTask(workflowTask);

        WorkflowModel workflow = newWorkflow(Arrays.asList(scheduled), 0);

        Duration result =
                ExecutorUtils.computePostpone(
                        workflow, Duration.ofSeconds(30), Duration.ofSeconds(3600));

        assertEquals(11, result.getSeconds());
    }

    @Test
    public void computePostponeDefaultsToZeroWhenNoEligibleTasks() {
        TaskModel completed = newTask(TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.COMPLETED);
        WorkflowModel workflow = newWorkflow(Arrays.asList(completed), 0);

        Duration result =
                ExecutorUtils.computePostpone(
                        workflow, Duration.ofSeconds(30), Duration.ofSeconds(3600));

        assertEquals(0, result.getSeconds());
    }

    @Test
    public void computePostponeUsesOffsetForWaitWithoutTimeout() {
        TaskModel waitTask = newTask(TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        waitTask.setWaitTimeout(0);
        WorkflowModel workflow = newWorkflow(Arrays.asList(waitTask), 0);

        Duration result =
                ExecutorUtils.computePostpone(
                        workflow, Duration.ofSeconds(30), Duration.ofSeconds(3600));

        assertEquals(30, result.getSeconds());
    }

    private WorkflowModel newWorkflow(List<TaskModel> tasks, long timeoutSeconds) {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setTimeoutSeconds(timeoutSeconds);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflowId");
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setTasks(tasks);
        return workflow;
    }

    private TaskModel newTask(String taskType, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setTaskId("taskId-" + taskType + "-" + status);
        return task;
    }
}
