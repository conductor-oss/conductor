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
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class LambdaAndTerminateTaskTest extends AbstractSpecification {

    private static final String WORKFLOW_WITH_TERMINATE_TASK = "test_terminate_task_wf";
    private static final String WORKFLOW_WITH_TERMINATE_TASK_FAILED =
            "test_terminate_task_failed_wf";
    private static final String WORKFLOW_WITH_LAMBDA_TASK = "test_lambda_wf";
    private static final String PARENT_WORKFLOW_WITH_TERMINATE_TASK =
            "test_terminate_task_parent_wf";
    private static final String WORKFLOW_WITH_DECISION_AND_TERMINATE =
            "ConditionalTerminateWorkflow";

    @Autowired SubWorkflow subWorkflowTask;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "failure_workflow_for_terminate_task_workflow.json",
                "terminate_task_completed_workflow_integration_test.json",
                "terminate_task_failed_workflow_integration.json",
                "simple_lambda_workflow_integration_test.json",
                "terminate_task_parent_workflow.json",
                "terminate_task_sub_workflow.json",
                "decision_and_terminate_integration_test.json");
    }

    @Test
    @DisplayName("Test workflow with a terminate task when the status is completed")
    void testWorkflowWithTerminateTaskWhenStatusIsCompleted() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the terminate task
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_TERMINATE_TASK, 1, "", workflowInput, null);

        // then: Ensure that the workflow has started and the first task is in scheduled state
        // and workflow output should be terminate task's output
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue(
                workflow.getReasonForIncompletion()
                        .contains("Workflow is COMPLETED by TERMINATE task"));
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(0).getTaskType());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("TERMINATE", workflow.getTasks().get(1).getTaskType());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertEquals(1, workflow.getOutput().size());
        assertEquals("[result:[testvalue:true]]", workflow.getOutput().toString());
    }

    @Test
    @DisplayName("Test workflow with a terminate task when the status is failed")
    void testWorkflowWithTerminateTaskWhenStatusIsFailed() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the terminate task
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_TERMINATE_TASK_FAILED, 1, "", workflowInput, null);

        // then: Verify that the workflow has failed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("Early exit in terminate", workflow.getReasonForIncompletion());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(0).getTaskType());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("TERMINATE", workflow.getTasks().get(1).getTaskType());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertNotNull(workflow.getOutput());

        String failedWorkflowId = (String) workflow.getOutput().get("conductor.failure_workflow");
        WorkflowModel failedWorkflow =
                workflowExecutionService.getWorkflowModel(failedWorkflowId, true);
        assertEquals(WorkflowModel.Status.COMPLETED, failedWorkflow.getStatus());
        assertEquals(workflowInstanceId, failedWorkflow.getInput().get("workflowId"));
        assertEquals(1, failedWorkflow.getTasks().size());
        assertEquals("LAMBDA", failedWorkflow.getTasks().get(0).getTaskType());
    }

    @Test
    @DisplayName("Test workflow with a terminate task when the workflow has a subworkflow")
    void testWorkflowWithTerminateTaskWhenWorkflowHasSubworkflow() throws Exception {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the terminate task
        String workflowInstanceId =
                startWorkflow(PARENT_WORKFLOW_WITH_TERMINATE_TASK, 1, "", workflowInput, null);

        // and: the sub workflow system task is executed
        Task lambdaSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TaskType.TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && Task.Status.SCHEDULED == t.getStatus())
                        .findFirst()
                        .orElse(null);
        if (lambdaSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, lambdaSubWfTask.getTaskId());
        }

        // then: verify that the workflow has started and the tasks are as expected
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals("lambdaTask1", workflow.getTasks().get(1).getReferenceTaskName());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(2).getTaskType());
        assertEquals("lambdaTask2", workflow.getTasks().get(2).getReferenceTaskName());
        assertEquals(3, workflow.getTasks().get(2).getSeq());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(4, workflow.getTasks().get(3).getSeq());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(4).getTaskType());
        assertEquals(5, workflow.getTasks().get(4).getSeq());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(5).getTaskType());
        assertEquals(6, workflow.getTasks().get(5).getSeq());

        // when: subworkflow is retrieved
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowId =
                workflow.getTaskByRefName("test_terminate_subworkflow").getSubWorkflowId();
        sweep(subWorkflowId);

        // then: verify that the sub workflow is RUNNING, and the task within is in SCHEDULED state
        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertEquals("integration_task_3", subWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

        // when: Complete the WAIT task that should cause the TERMINATE task to execute
        Task waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(5);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // then: Verify that the workflow has completed and the SUB_WORKFLOW is not still
        // IN_PROGRESS
        // (should be SKIPPED)
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertTrue(
                workflow.getReasonForIncompletion()
                        .contains("Workflow is COMPLETED by TERMINATE task"));
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(1).getTaskType());
        assertEquals("lambdaTask1", workflow.getTasks().get(1).getReferenceTaskName());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(2).getTaskType());
        assertEquals("lambdaTask2", workflow.getTasks().get(2).getReferenceTaskName());
        assertEquals(3, workflow.getTasks().get(2).getSeq());
        assertEquals(Task.Status.CANCELED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(4, workflow.getTasks().get(3).getSeq());
        assertEquals(Task.Status.CANCELED, workflow.getTasks().get(4).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(4).getTaskType());
        assertEquals(5, workflow.getTasks().get(4).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(5).getTaskType());
        assertEquals(6, workflow.getTasks().get(5).getSeq());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
        assertEquals("TERMINATE", workflow.getTasks().get(6).getTaskType());
        assertEquals(7, workflow.getTasks().get(6).getSeq());

        // and: ensure that the subworkflow is terminated
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertTrue(
                subWorkflow
                        .getReasonForIncompletion()
                        .contains(
                                "Parent workflow has been terminated with reason: Workflow is COMPLETED by TERMINATE task"));
        assertEquals("integration_task_3", subWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, subWorkflow.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Test workflow with a terminate task within a decision branch")
    void testWorkflowWithTerminateTaskWithinDecisionBranch() throws Exception {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1");
        workflowInput.put("param2", "p2");
        workflowInput.put("case", "two");

        // when: The workflow is started
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_DECISION_AND_TERMINATE, 1, "", workflowInput, null);

        // then: verify that the workflow is in RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(1, workflow.getTasks().get(0).getSeq());

        // when: the task 'integration_task_1' is polled and completed
        Task polledAndCompletedTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1 completed"));

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has FAILED
        // due to terminate task
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(1, workflow.getOutput().size());
        assertEquals("[output:task1 completed]", workflow.getOutput().toString());
        assertTrue(
                workflow.getReasonForIncompletion()
                        .contains("Workflow is FAILED by TERMINATE task"));
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("task1 completed", workflow.getTasks().get(0).getOutputData().get("op"));
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertEquals("TERMINATE", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(3, workflow.getTasks().get(2).getSeq());
    }

    @Test
    @DisplayName("Test workflow with lambda task")
    void testWorkflowWithLambdaTask() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the lambda task
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_LAMBDA_TASK, 1, "", workflowInput, null);

        // then: verify that the task is completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("LAMBDA", workflow.getTasks().get(0).getTaskType());
        assertEquals(
                "[result:[testvalue:true]]", workflow.getTasks().get(0).getOutputData().toString());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
    }
}
