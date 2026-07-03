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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedLargePayloadTask;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class WaitTaskTest extends AbstractSpecification {

    private static final String WAIT_BASED_WORKFLOW = "test_wait_workflow";
    private static final String SET_VARIABLE_WORKFLOW = "set_variable_workflow_integration_test";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "wait_workflow_integration_test.json",
                "set_variable_workflow_integration_test.json");
    }

    @Test
    @DisplayName("Test workflow with set variable task")
    void testWorkflowWithSetVariableTask() throws Exception {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("var", "var_test_value");

        // when: Start the workflow which has the set variable task
        String workflowInstanceId =
                startWorkflow(SET_VARIABLE_WORKFLOW, 1, "", workflowInput, null);

        // then: verify that the simple task is scheduled
        Workflow execution = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, execution.getStatus());
        assertEquals(1, execution.getTasks().size());
        assertEquals("simple", execution.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, execution.getTasks().get(0).getStatus());

        // when: poll and complete the 'simple' with external payload storage
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteTask(
                        "simple", "simple.worker", Map.of("ok1", "ov1"));

        // then: verify that the 'simple' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // then: ensure that the wait task is completed and the next task is scheduled
        Workflow execution2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, execution2.getStatus());
        assertEquals(3, execution2.getTasks().size());
        assertEquals("simple", execution2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution2.getTasks().get(0).getStatus());
        assertEquals("SET_VARIABLE", execution2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution2.getTasks().get(1).getStatus());
        assertEquals("WAIT", execution2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, execution2.getTasks().get(2).getStatus());
        assertEquals(Map.of("var", "var_test_value"), execution2.getVariables());

        // when: The wait task is completed
        Task waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(2);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // then: ensure that the wait task is completed and the workflow is completed
        Workflow execution3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, execution3.getStatus());
        assertEquals(3, execution3.getTasks().size());
        assertEquals("simple", execution3.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution3.getTasks().get(0).getStatus());
        assertEquals("SET_VARIABLE", execution3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution3.getTasks().get(1).getStatus());
        assertEquals("WAIT", execution3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution3.getTasks().get(2).getStatus());
        assertEquals(Map.of("var", "var_test_value"), execution3.getVariables());
        assertEquals(Map.of("variables", Map.of("var", "var_test_value")), execution3.getOutput());
    }

    @Test
    @DisplayName("Verify that a wait based simple workflow is executed")
    void verifyThatAWaitBasedSimpleWorkflowIsExecuted() throws Exception {
        // when: Start a wait task based workflow
        String workflowInstanceId =
                startWorkflow(WAIT_BASED_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: Retrieve the workflow
        Workflow execution = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, execution.getStatus());
        assertEquals(1, execution.getTasks().size());
        assertEquals(TaskType.WAIT.name(), execution.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, execution.getTasks().get(0).getStatus());

        // when: The wait task is completed
        Task waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(0);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // then: ensure that the wait task is completed and the next task is scheduled
        Workflow execution2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, execution2.getStatus());
        assertEquals(2, execution2.getTasks().size());
        assertEquals(TaskType.WAIT.name(), execution2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution2.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", execution2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, execution2.getTasks().get(1).getStatus());

        // when: The integration_task_1 is polled and completed
        Task polledAndCompletedTry1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");

        // then: verify that the task was polled and completed and the workflow is in a complete
        // state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1);
        Workflow execution3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, execution3.getStatus());
        assertEquals(2, execution3.getTasks().size());
        assertEquals("integration_task_1", execution3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, execution3.getTasks().get(1).getStatus());
    }
}
