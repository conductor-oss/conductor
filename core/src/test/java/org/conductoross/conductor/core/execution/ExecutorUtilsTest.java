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

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExecutorUtilsTest {

    private static final Duration WORKFLOW_OFFSET = Duration.ofSeconds(30);

    private WorkflowModel workflowWithTask(String taskType, TaskModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("test-workflow");
        TaskModel task = new TaskModel();
        task.setTaskId("task1");
        task.setTaskType(taskType);
        task.setStatus(status);
        workflow.setTasks(List.of(task));
        return workflow;
    }

    @Test
    public void testHumanTaskInProgressPostponesToWorkflowOffset() {
        WorkflowModel workflow =
                workflowWithTask(TaskType.TASK_TYPE_HUMAN, TaskModel.Status.IN_PROGRESS);
        Duration result = ExecutorUtils.computePostpone(workflow, WORKFLOW_OFFSET);
        assertEquals(
                WORKFLOW_OFFSET.getSeconds(),
                result.getSeconds(),
                "HUMAN task should postpone by workflowOffsetTimeout");
    }

    @Test
    public void testWaitTaskWithNoTimeoutPostponesToWorkflowOffset() {
        WorkflowModel workflow =
                workflowWithTask(TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        // waitTimeout defaults to 0 — no deadline
        Duration result = ExecutorUtils.computePostpone(workflow, WORKFLOW_OFFSET);
        assertEquals(
                WORKFLOW_OFFSET.getSeconds(),
                result.getSeconds(),
                "WAIT task with no timeout should postpone by workflowOffsetTimeout");
    }

    @Test
    public void testWaitTaskWithFutureTimeoutPostponesToRemainingTime() {
        WorkflowModel workflow =
                workflowWithTask(TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        // Set waitTimeout to 60 seconds from now
        long waitTimeout = System.currentTimeMillis() + 60_000;
        workflow.getTasks().get(0).setWaitTimeout(waitTimeout);

        Duration result = ExecutorUtils.computePostpone(workflow, WORKFLOW_OFFSET);
        // Should be at most 30s (capped to workflowOffsetTimeout) and greater than 0
        assertEquals(
                WORKFLOW_OFFSET.getSeconds(),
                result.getSeconds(),
                "WAIT task with future timeout > workflowOffset should be capped to workflowOffset");
    }

    @Test
    public void testWaitTaskWithExpiredTimeoutPostponesToWorkflowOffset() {
        WorkflowModel workflow =
                workflowWithTask(TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        // Set waitTimeout to 10 seconds ago (elapsed)
        long waitTimeout = System.currentTimeMillis() - 10_000;
        workflow.getTasks().get(0).setWaitTimeout(waitTimeout);

        Duration result = ExecutorUtils.computePostpone(workflow, WORKFLOW_OFFSET);
        // postponeDurationSeconds == 0 (elapsed), so falls back to workflowOffsetTimeout.
        // The decider itself handles the timeout transition, not the sweep timer.
        assertEquals(
                WORKFLOW_OFFSET.getSeconds(),
                result.getSeconds(),
                "WAIT task with elapsed timeout should fall back to workflowOffsetTimeout");
    }

    @Test
    public void testNonHumanTaskWithNoResponseTimeoutPostponesToWorkflowOffset() {
        WorkflowModel workflow = workflowWithTask("SIMPLE", TaskModel.Status.IN_PROGRESS);
        Duration result = ExecutorUtils.computePostpone(workflow, WORKFLOW_OFFSET);
        assertEquals(
                WORKFLOW_OFFSET.getSeconds(),
                result.getSeconds(),
                "Task with no response timeout should fall back to workflowOffsetTimeout");
    }
}
