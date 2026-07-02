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
package com.netflix.conductor.test.integration;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;
import com.netflix.conductor.test.utils.UserTask;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class TaskLimitsWorkflowTest extends AbstractSpecification {

    @Autowired QueueDAO queueDAO;

    @Autowired UserTask userTask;

    private static final String RATE_LIMITED_SYSTEM_TASK_WORKFLOW =
            "test_rate_limit_system_task_workflow";
    private static final String RATE_LIMITED_SIMPLE_TASK_WORKFLOW =
            "test_rate_limit_simple_task_workflow";
    private static final String CONCURRENCY_EXECUTION_LIMITED_WORKFLOW =
            "test_concurrency_limits_workflow";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "rate_limited_system_task_workflow_integration_test.json",
                "rate_limited_simple_task_workflow_integration_test.json",
                "concurrency_limited_task_workflow_integration_test.json");
    }

    @Test
    @DisplayName("Verify that the rate limiting for system tasks is honored")
    void verifyRateLimitingForSystemTasksIsHonored() {
        // when: Start a workflow that has a rate limited system task in it
        String workflowInstanceId =
                startWorkflow(RATE_LIMITED_SYSTEM_TASK_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("USER_TASK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: Execute the user task
        Task scheduledTask1 =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(0);
        asyncSystemTaskExecutor.execute(userTask, scheduledTask1.getTaskId());

        // then: Verify the state of the workflow is completed
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(1, completedWorkflow.getTasks().size());
        assertEquals("USER_TASK", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());

        // when: A new instance of the workflow is started
        String workflowTwoInstanceId =
                startWorkflow(RATE_LIMITED_SYSTEM_TASK_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflowTwo =
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowTwo.getStatus());
        assertEquals(1, workflowTwo.getTasks().size());
        assertEquals("USER_TASK", workflowTwo.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflowTwo.getTasks().get(0).getStatus());

        // when: Execute the user task on the second workflow
        Task scheduledTask2 =
                workflowExecutionService
                        .getExecutionStatus(workflowTwoInstanceId, true)
                        .getTasks()
                        .get(0);
        asyncSystemTaskExecutor.execute(userTask, scheduledTask2.getTaskId());

        // then: Verify the state of the workflow is still in running state
        Workflow workflowTwoAfterExec =
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowTwoAfterExec.getStatus());
        assertEquals(1, workflowTwoAfterExec.getTasks().size());
        assertEquals("USER_TASK", workflowTwoAfterExec.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflowTwoAfterExec.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify that the rate limiting for simple tasks is honored")
    void verifyRateLimitingForSimpleTasksIsHonored() throws InterruptedException {
        // when: Start a workflow that has a rate limited simple task in it
        String workflowInstanceId =
                startWorkflow(RATE_LIMITED_SIMPLE_TASK_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("test_simple_task_with_rateLimits", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: polling and completing the task
        Task polledAndCompletedTask =
                workflowTestUtil.pollAndCompleteTask(
                        "test_simple_task_with_rateLimits", "rate.limit.test.worker");

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask);

        // and: the workflow is completed
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(1, completedWorkflow.getTasks().size());
        assertEquals(
                "test_simple_task_with_rateLimits",
                completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());

        // when: A new instance of the workflow is started
        String workflowTwoInstanceId =
                startWorkflow(RATE_LIMITED_SIMPLE_TASK_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflowTwo =
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowTwo.getStatus());
        assertEquals(1, workflowTwo.getTasks().size());
        assertEquals(
                "test_simple_task_with_rateLimits", workflowTwo.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflowTwo.getTasks().get(0).getStatus());

        // when: polling for the task
        Task polledTask =
                workflowExecutionService.poll(
                        "test_simple_task_with_rateLimits", "rate.limit.test.worker");

        // then: verify that no task is returned
        assertNull(polledTask);

        // when: sleep for 10 seconds to ensure rate limit duration is past
        Thread.sleep(10000L);

        // and: the task offset time is reset to ensure that a task is returned on the next poll
        queueDAO.resetOffsetTime(
                "test_simple_task_with_rateLimits",
                workflowExecutionService
                        .getExecutionStatus(workflowTwoInstanceId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId());

        // and: polling and completing the task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "test_simple_task_with_rateLimits", "rate.limit.test.worker");

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);

        // and: the workflow is completed
        Workflow completedWorkflowTwo =
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflowTwo.getStatus());
        assertEquals(1, completedWorkflowTwo.getTasks().size());
        assertEquals(
                "test_simple_task_with_rateLimits",
                completedWorkflowTwo.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflowTwo.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify that concurrency limited tasks are honored during workflow execution")
    void verifyConcurrencyLimitedTasksAreHonoredDuringWorkflowExecution() {
        // when: Start a workflow that has a concurrency execution limited task in it
        String workflowInstanceId =
                startWorkflow(CONCURRENCY_EXECUTION_LIMITED_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("test_task_with_concurrency_limit", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: The task is polled and acknowledged
        Task polledTask1 =
                workflowExecutionService.poll(
                        "test_task_with_concurrency_limit", "test_task_worker");

        // then: Verify that the task was polled and acknowledged
        assertEquals("test_task_with_concurrency_limit", polledTask1.getTaskType());
        assertEquals(workflowInstanceId, polledTask1.getWorkflowInstanceId());

        // when: A additional workflow that has a concurrency execution limited task in it
        String workflowTwoInstanceId =
                startWorkflow(CONCURRENCY_EXECUTION_LIMITED_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is in a running state
        Workflow workflowTwo =
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowTwo.getStatus());
        assertEquals(1, workflowTwo.getTasks().size());
        assertEquals(
                "test_task_with_concurrency_limit", workflowTwo.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflowTwo.getTasks().get(0).getStatus());

        // when: The task is polled
        Task polledTaskTry1 =
                workflowExecutionService.poll(
                        "test_task_with_concurrency_limit", "test_task_worker");

        // then: Verify that there is no task returned
        assertNull(polledTaskTry1);

        // when: The task that was polled and acknowledged is completed
        polledTask1.setStatus(Task.Status.COMPLETED);
        workflowExecutionService.updateTask(new TaskResult(polledTask1));

        // and: The task offset time is reset to ensure that a task is returned on the next poll
        queueDAO.resetOffsetTime(
                "test_task_with_concurrency_limit",
                workflowExecutionService
                        .getExecutionStatus(workflowTwoInstanceId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId());

        // then: Verify that the first workflow is in a completed state
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(1, completedWorkflow.getTasks().size());
        assertEquals(
                "test_task_with_concurrency_limit",
                completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());

        // and: The task is polled again and acknowledged
        Task polledTaskTry2 =
                workflowExecutionService.poll(
                        "test_task_with_concurrency_limit", "test_task_worker");

        // then: Verify that the task is returned since there are no tasks in progress
        assertNotNull(polledTaskTry2);
        assertEquals("test_task_with_concurrency_limit", polledTaskTry2.getTaskType());
        assertEquals(workflowTwoInstanceId, polledTaskTry2.getWorkflowInstanceId());
    }
}
