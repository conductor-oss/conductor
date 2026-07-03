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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class DoWhileTest extends AbstractSpecification {

    @Autowired Join joinTask;

    @Autowired SubWorkflow subWorkflowTask;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "do_while_integration_test.json",
                "do_while_multiple_integration_test.json",
                "do_while_as_subtask_integration_test.json",
                "simple_one_task_sub_workflow_integration_test.json",
                "do_while_iteration_fix_test.json",
                "do_while_sub_workflow_integration_test.json",
                "do_while_five_loop_over_integration_test.json",
                "do_while_system_tasks.json",
                "do_while_with_decision_task.json",
                "do_while_set_variable_fix.json",
                "do_while_high_iteration_test.json");
    }

    @Test
    @DisplayName("Test workflow with 2 iterations of five tasks")
    void testWorkflowWith2IterationsOfFiveTasks() {
        // given: Number of iterations of the loop is set to 2
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 2);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow(
                        "do_while_five_loop_over_integration_test",
                        1,
                        "looptest",
                        workflowInput,
                        null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(1, workflow.getTasks().get(1).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(1, workflow.getTasks().get(2).getIteration());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals(1, workflow.getTasks().get(3).getIteration());

        // when: Polling and completing first task
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing second task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        verifyTaskIteration(polledAndCompletedTask2, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(9, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals(2, workflow.getTasks().get(6).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals(2, workflow.getTasks().get(7).getIteration());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(8).getStatus());
        assertEquals(2, workflow.getTasks().get(8).getIteration());

        // when: Polling and completing first task (second iteration)
        polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 2);

        // when: Polling and completing second task (second iteration)
        polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        verifyTaskIteration(polledAndCompletedTask2, 2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(12, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals(2, workflow.getTasks().get(6).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals(2, workflow.getTasks().get(7).getIteration());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(8).getStatus());
        assertEquals(2, workflow.getTasks().get(8).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(9).getStatus());
        assertEquals(2, workflow.getTasks().get(9).getIteration());
        assertEquals("integration_task_2", workflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(10).getStatus());
        assertEquals(2, workflow.getTasks().get(10).getIteration());
        assertEquals("integration_task_3", workflow.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(11).getStatus());
        assertEquals(0, workflow.getTasks().get(11).getIteration()); // this is outside DO_WHILE

        // when: Polling and completing last task
        polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in completed state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(12, workflow.getTasks().size());
        assertEquals("integration_task_3", workflow.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(11).getStatus());
        assertEquals(0, workflow.getTasks().get(11).getIteration());
    }

    @Test
    @DisplayName("Test workflow with 2 iterations of 3 system tasks")
    void testWorkflowWith2IterationsOf3SystemTasks() {
        // given: Number of iterations of the loop is set to 2
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 2);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("do_while_system_tasks", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(8, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(1, workflow.getTasks().get(1).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(1, workflow.getTasks().get(2).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals(1, workflow.getTasks().get(3).getIteration());
        assertEquals("LAMBDA", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals(2, workflow.getTasks().get(4).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals(2, workflow.getTasks().get(5).getIteration());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals(2, workflow.getTasks().get(6).getIteration());
        assertEquals("integration_task_1", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(7).getStatus());
        assertEquals(0, workflow.getTasks().get(7).getIteration()); // outside the loop

        // when: Polling and completing first task
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in completed state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(8, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals(0, workflow.getTasks().get(7).getIteration()); // outside the loop
    }

    @Test
    @DisplayName("Test workflow with a single iteration Do While task")
    void testWorkflowWithSingleIterationDoWhileTask() {
        // given: Number of iterations of the loop is set to 1
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 1);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("Do_While_Workflow", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: Polling and completing first task
        Task polledAndCompletedTask0 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_0", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
        verifyTaskIteration(polledAndCompletedTask0, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing second task
        String joinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join__1")
                        .getTaskId();
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing third task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // and: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinId);

        // then: Verify that the task was polled and acknowledged and workflow is in completed state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        verifyTaskIteration(polledAndCompletedTask2, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
    }

    @Test
    @DisplayName("Test workflow with a single iteration Do While task with Sub workflow")
    void testWorkflowWithSingleIterationDoWhileTaskWithSubWorkflow() {
        // given: Number of iterations of the loop is set to 1
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 1);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("Do_While_Sub_Workflow", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: Polling and completing first task
        Task polledAndCompletedTask0 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_0", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
        verifyTaskIteration(polledAndCompletedTask0, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing second task
        String joinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join__1")
                        .getTaskId();
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing third task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // and: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinId);

        // and: the sub workflow system task is executed
        Task doWhileSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (doWhileSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, doWhileSubWfTask.getTaskId());
        }

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        verifyTaskIteration(polledAndCompletedTask2, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

        // then: verify that the sub workflow task is in a IN PROGRESS state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

        // when: sub workflow is retrieved
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowInstanceId = workflow.getTaskByRefName("st1__1").getSubWorkflowId();
        sweep(subWorkflowInstanceId);

        // then: verify that the sub workflow is in a RUNNING state
        Workflow subWorkflow =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertEquals("simple_task_in_sub_wf", subWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

        // when: the 'simple_task_in_sub_wf' belonging to the sub workflow is polled and completed
        Task polledAndCompletedSubWorkflowTask =
                workflowTestUtil.pollAndCompleteTask(
                        "simple_task_in_sub_wf", "subworkflow.task.worker");

        // then: verify that the task was polled and acknowledged
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndCompletedSubWorkflowTask);

        // and: verify that the sub workflow is in COMPLETED state
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWorkflow.getTasks().get(0).getTaskType());

        // and: the parent workflow is swept
        sweep(workflowInstanceId);

        // and: verify that the workflow is in COMPLETED state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
    }

    @Test
    @DisplayName("Test workflow with multiple Do While tasks with multiple iterations")
    void testWorkflowWithMultipleDoWhileTasksWithMultipleIterations() {
        // given: Number of iterations of the first loop is set to 2 and second loop is set to 1
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 2);
        workflowInput.put("loop2", 1);

        // when: A workflow with multiple do while tasks with multiple iterations is started
        String workflowInstanceId =
                startWorkflow("Do_While_Multiple", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: Polling and completing first task
        Task polledAndCompletedTask0 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_0", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
        verifyTaskIteration(polledAndCompletedTask0, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing second task
        String join1Id =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join__1")
                        .getTaskId();
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing third task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // and: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, join1Id);

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        verifyTaskIteration(polledAndCompletedTask2, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());

        // when: Polling and completing second iteration of first task
        Task polledAndCompletedSecondIterationTask0 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_0", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask0, new HashMap<>());
        verifyTaskIteration(polledAndCompletedSecondIterationTask0, 2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(11, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("FORK", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(8).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(9).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(10).getStatus());

        // when: Polling and completing second iteration of second task
        String join2Id =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join__2")
                        .getTaskId();
        Task polledAndCompletedSecondIterationTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask1);
        verifyTaskIteration(polledAndCompletedSecondIterationTask1, 2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(11, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("FORK", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(8).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(9).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(10).getStatus());

        // when: Polling and completing second iteration of third task
        Task polledAndCompletedSecondIterationTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // and: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, join2Id);

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask2);
        verifyTaskIteration(polledAndCompletedSecondIterationTask2, 2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(13, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("FORK", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(8).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(9).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(10).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(11).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(12).getStatus());

        // when: Polling and completing task within the second do while
        Task polledAndCompletedIntegrationTask3 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in completed state
        verifyPolledAndAcknowledgedTask(polledAndCompletedIntegrationTask3);
        verifyTaskIteration(polledAndCompletedIntegrationTask3, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(13, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("FORK", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(8).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(9).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(10).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(11).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(12).getStatus());
    }

    @Test
    @DisplayName("Test retrying a failed do while workflow")
    void testRetryingAFailedDoWhileWorkflow() {
        // setup: Update the task definition with no retries
        String taskName = "integration_task_0";
        TaskDef persistedTaskDefinition =
                workflowTestUtil.getPersistedTaskDefinition(taskName).get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        persistedTaskDefinition.getName(),
                        persistedTaskDefinition.getDescription(),
                        persistedTaskDefinition.getOwnerEmail(),
                        0,
                        persistedTaskDefinition.getTimeoutSeconds(),
                        persistedTaskDefinition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            // when: A do while workflow is started
            Map<String, Object> workflowInput = new HashMap<>();
            workflowInput.put("loop", 1);
            String workflowInstanceId =
                    startWorkflow("Do_While_Workflow", 1, "looptest", workflowInput, null);

            // then: Verify that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

            // when: Polling and failing first task
            Task polledAndFailedTask0 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_0", "integration.test.worker", "induced..failure");

            // then: Verify that the task was polled and acknowledged and workflow is in failed
            // state
            verifyPolledAndAcknowledgedTask(polledAndFailedTask0);
            verifyTaskIteration(polledAndFailedTask0, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());

            // when: The workflow is retried
            workflowExecutor.retry(workflowInstanceId, false);

            // then: Verify that workflow is running
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

            // when: Polling and completing first task
            Task polledAndCompletedTask0 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_0", "integration.test.worker");

            // then: Verify that the task was polled and acknowledged and workflow is in running
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
            verifyTaskIteration(polledAndCompletedTask0, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

            // when: Polling and completing second task
            String joinId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("join__1")
                            .getTaskId();
            Task polledAndCompletedTask1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "integration.test.worker");

            // then: Verify that the task was polled and acknowledged and workflow is in running
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
            verifyTaskIteration(polledAndCompletedTask1, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

            // when: Polling and completing third task
            Task polledAndCompletedTask2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "integration.test.worker");

            // and: JOIN task is executed
            asyncSystemTaskExecutor.execute(joinTask, joinId);

            // then: Verify that the task was polled and acknowledged and workflow is in completed
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
            verifyTaskIteration(polledAndCompletedTask2, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        } finally {
            // cleanup: Reset the task definition
            metadataService.updateTaskDef(persistedTaskDefinition);
        }
    }

    @Test
    @DisplayName("Test auto retrying a failed do while workflow")
    void testAutoRetryingAFailedDoWhileWorkflow() {
        // setup: Update the task definition with retryCount to 1 and retryDelaySeconds to 0
        String taskName = "integration_task_0";
        TaskDef persistedTaskDefinition =
                workflowTestUtil.getPersistedTaskDefinition(taskName).get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        persistedTaskDefinition.getName(),
                        persistedTaskDefinition.getDescription(),
                        persistedTaskDefinition.getOwnerEmail(),
                        1,
                        persistedTaskDefinition.getTimeoutSeconds(),
                        persistedTaskDefinition.getResponseTimeoutSeconds());
        modifiedTaskDefinition.setRetryDelaySeconds(0);
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            // when: A do while workflow is started
            Map<String, Object> workflowInput = new HashMap<>();
            workflowInput.put("loop", 1);
            String workflowInstanceId =
                    startWorkflow("Do_While_Workflow", 1, "looptest", workflowInput, null);

            // then: Verify that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

            // when: Polling and failing first task
            Task polledAndFailedTask0 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_0", "integration.test.worker", "induced..failure");

            // then: Verify that the task was polled and acknowledged and retried task was generated
            verifyPolledAndAcknowledgedTask(polledAndFailedTask0);
            verifyTaskIteration(polledAndFailedTask0, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertTrue(workflow.getTasks().get(1).isRetried());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertEquals(1, workflow.getTasks().get(2).getRetryCount());
            assertEquals(
                    workflow.getTasks().get(1).getTaskId(),
                    workflow.getTasks().get(2).getRetriedTaskId());

            // when: Polling and completing first task
            Task polledAndCompletedTask0 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_0", "integration.test.worker");

            // then: Verify that the task was polled and acknowledged and workflow is in running
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
            verifyTaskIteration(polledAndCompletedTask0, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

            // when: Polling and completing second task
            String joinId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("join__1")
                            .getTaskId();
            Task polledAndCompletedTask1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "integration.test.worker");

            // then: Verify that the task was polled and acknowledged and workflow is in running
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
            verifyTaskIteration(polledAndCompletedTask1, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());

            // when: Polling and completing third task
            Task polledAndCompletedTask2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "integration.test.worker");

            // and: JOIN task is executed
            asyncSystemTaskExecutor.execute(joinTask, joinId);

            // then: Verify that the task was polled and acknowledged and workflow is in completed
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
            verifyTaskIteration(polledAndCompletedTask2, 1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_0", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals("FORK", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        } finally {
            // cleanup: Reset the task definition
            metadataService.updateTaskDef(persistedTaskDefinition);
        }
    }

    @Test
    @DisplayName("Test workflow with a iteration Do While task as subtask of a forkjoin task")
    void testWorkflowWithIterationDoWhileTaskAsSubtaskOfForkjoinTask() {
        // given: Number of iterations of the loop is set to 1
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 1);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("Do_While_SubTask", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());

        // when: Polling and completing first task in DO While
        String joinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join")
                        .getTaskId();
        Task polledAndCompletedTask0 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_0", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0);
        verifyTaskIteration(polledAndCompletedTask0, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing second task in DO While
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "integration.test.worker");

        // then: Verify that the task was polled and acknowledged and workflow is in running state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);
        verifyTaskIteration(polledAndCompletedTask1, 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());

        // when: Polling and completing third task
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "integration.test.worker");

        // and: the workflow is evaluated
        sweep(workflowInstanceId);

        // and: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinId);

        // then: Verify that the task was polled and acknowledged and workflow is in completed state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("DO_WHILE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_0", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
    }

    @Test
    @DisplayName(
            "Test workflow with Do While task contains loop over task that use iteration in script expression")
    void testWorkflowWithDoWhileTaskContainsLoopOverTaskThatUseIterationInScriptExpression() {
        // given: Number of iterations of the loop is set to 2
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 2);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow(
                        "Do_While_Workflow_Iteration_Fix", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(0, workflow.getTasks().get(1).getOutputData().get("result"));
        assertEquals("LAMBDA", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(1, workflow.getTasks().get(2).getOutputData().get("result"));
    }

    @Test
    @DisplayName("Test workflow with Do While task contains set variable task")
    void testWorkflowWithDoWhileTaskContainsSetVariableTask() {
        // given: The loop condition is set to use set variable
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("value", 2);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("do_while_Set_variable_fix", 1, "looptest", workflowInput, null);

        // then: Verify that the workflow has completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("SET_VARIABLE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("0", workflow.getTasks().get(1).getInputData().get("value"));
    }

    @Test
    @DisplayName("Test workflow with Do While task contains decision task")
    void testWorkflowWithDoWhileTaskContainsDecisionTask() {
        // given: The loop condition is set to use set variable
        Map<String, Object> workflowInput = new HashMap<>();
        List<Object> array = new ArrayList<>();
        array.add(1);
        array.add(2);
        workflowInput.put("list", array);

        // when: A do_while workflow is started
        String workflowInstanceId =
                startWorkflow("DO_While_with_Decision_task", 1, "looptest", workflowInput, null);

        // then: Verify that the loop over task is waiting for the wait task to get completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());

        // when: The wait task is completed
        Task waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(3);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // then: Verify that the next iteration is scheduled and workflow is in running state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(8, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
        assertEquals(2, workflow.getTasks().get(0).getIteration());
        assertEquals("INLINE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(7).getStatus());

        // when: The wait task is completed (second time)
        waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(7);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // then: Verify that the workflow is completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(9, workflow.getTasks().size());
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(2, workflow.getTasks().get(0).getIteration());
        assertEquals("INLINE", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        assertEquals("INLINE", workflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(8).getStatus());
    }

    /**
     * Regression test for GitHub issue #799 / PR #822 — overflow only.
     *
     * <p>Before the fix, WorkflowExecutorOps.decide() called itself recursively each time a
     * synchronous system task (e.g. LAMBDA inside a DO_WHILE) changed workflow state. At high
     * iteration counts (~400+) this produced a StackOverflowError.
     *
     * <p>The fix replaces the recursive call with an iterative loop. This test verifies that a
     * DO_WHILE with 500 synchronous LAMBDA iterations completes without error.
     */
    @Test
    @DisplayName("Test DO_WHILE with 500 LAMBDA iterations completes without StackOverflowError")
    void testDoWhileWith500LambdaIterationsCompletesWithoutStackOverflowError() {
        // given: A DO_WHILE workflow set to run 500 LAMBDA iterations
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 500);

        // when: The workflow is started
        String workflowInstanceId =
                startWorkflow(
                        "do_while_high_iteration_test",
                        1,
                        "overflow-regression",
                        workflowInput,
                        null);

        // then: The workflow completes successfully with all 500 LAMBDA tasks plus the DO_WHILE
        // task
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(501, workflow.getTasks().size()); // 1 DO_WHILE + 500 LAMBDA
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(500, workflow.getTasks().get(0).getIteration());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(1, workflow.getTasks().get(1).getIteration());
        assertEquals("LAMBDA", workflow.getTasks().get(500).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(500).getStatus());
        assertEquals(500, workflow.getTasks().get(500).getIteration());
    }

    /**
     * Regression test for GitHub issue #799 / PR #822 — overflow AND wrong loop count.
     *
     * <p>The Do_While_Workflow_Iteration_Fix workflow uses ${loopTask['iteration']} in the LAMBDA
     * script to compute a 0-based index (iteration - 1). At high iteration counts the old recursive
     * decide() would either overflow OR produce a wrong iteration counter because the recursive
     * call re-entered the loop mid-execution.
     *
     * <p>This test verifies both that the workflow completes and that every LAMBDA task reports the
     * correct iteration-based output value.
     */
    @Test
    @DisplayName("Test DO_WHILE iteration counter is correct at 500 iterations (issue #799)")
    void testDoWhileIterationCounterIsCorrectAt500Iterations() {
        // given: A DO_WHILE workflow that reads the loop iteration counter in each LAMBDA task
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("loop", 500);

        // when: The workflow is started
        String workflowInstanceId =
                startWorkflow(
                        "Do_While_Workflow_Iteration_Fix",
                        1,
                        "iteration-count-regression",
                        workflowInput,
                        null);

        // then: The workflow completes and the last LAMBDA task reports the correct (0-based) index
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(501, workflow.getTasks().size()); // 1 DO_WHILE + 500 LAMBDA (form_uri)
        assertEquals("DO_WHILE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(500, workflow.getTasks().get(0).getIteration());
        // First iteration: loopTask.iteration == 1, so result == 0
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(0, workflow.getTasks().get(1).getOutputData().get("result"));
        // Last iteration: loopTask.iteration == 500, so result == 499
        assertEquals("LAMBDA", workflow.getTasks().get(500).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(500).getStatus());
        assertEquals(499, workflow.getTasks().get(500).getOutputData().get("result"));
    }

    private void verifyTaskIteration(Task task, int iteration) {
        assertTrue(
                task.getReferenceTaskName()
                        .endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())),
                "Expected reference task name to end with loop suffix for iteration "
                        + task.getIteration());
        assertEquals(iteration, task.getIteration());
    }
}
