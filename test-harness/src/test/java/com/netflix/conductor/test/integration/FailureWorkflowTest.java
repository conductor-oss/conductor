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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

import static org.junit.jupiter.api.Assertions.*;

class FailureWorkflowTest extends AbstractSpecification {

    private static final String WORKFLOW_WITH_TERMINATE_TASK_FAILED =
            "test_terminate_task_failed_wf";

    private static final String PARENT_WORKFLOW_WITH_FAILURE_TASK = "test_task_failed_parent_wf";

    @Autowired SubWorkflow subWorkflowTask;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "failure_workflow_for_terminate_task_workflow.json",
                "terminate_task_failed_workflow_integration.json",
                "test_task_failed_parent_workflow.json",
                "test_task_failed_sub_workflow.json");
    }

    @Test
    @DisplayName("Test workflow with a task that failed")
    void testWorkflowWithATaskThatFailed() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the failed task
        String testId = "testId";
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_TERMINATE_TASK_FAILED, 1, testId, workflowInput, null);

        // then: Verify that the workflow has failed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("Early exit in terminate", workflow.getReasonForIncompletion());

        List<Task> tasks = workflow.getTasks();
        assertEquals(Task.Status.COMPLETED, tasks.get(0).getStatus());
        assertEquals("LAMBDA", tasks.get(0).getTaskType());
        assertEquals(1, tasks.get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, tasks.get(1).getStatus());
        assertEquals("TERMINATE", tasks.get(1).getTaskType());
        assertEquals(2, tasks.get(1).getSeq());

        assertNotNull(workflow.getOutput());
        String failedWorkflowId = (String) workflow.getOutput().get("conductor.failure_workflow");
        String workflowCorrelationId = workflow.getCorrelationId();
        String workflowFailureTaskId = tasks.get(1).getTaskId();

        WorkflowModel failureWorkflow =
                workflowExecutionService.getWorkflowModel(failedWorkflowId, true);
        assertEquals(WorkflowModel.Status.COMPLETED, failureWorkflow.getStatus());
        assertEquals(workflowCorrelationId, failureWorkflow.getCorrelationId());
        assertEquals(workflowInstanceId, failureWorkflow.getInput().get("workflowId"));
        assertEquals(workflowFailureTaskId, failureWorkflow.getInput().get("failureTaskId"));
        assertEquals(1, failureWorkflow.getTasks().size());
        assertEquals("LAMBDA", failureWorkflow.getTasks().get(0).getTaskType());
        assertNotNull(failureWorkflow.getInput().get("failedWorkflow"));
    }

    @Test
    @DisplayName("Test workflow with a task failed in subworkflow")
    void testWorkflowWithATaskFailedInSubworkflow() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("a", 1);

        // when: Start the workflow which has the subworkflow task
        String workflowInstanceId =
                startWorkflow(PARENT_WORKFLOW_WITH_FAILURE_TASK, 1, "", workflowInput, null);

        // and: the sub workflow system task is executed
        Task failureSubWfTask =
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
        if (failureSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, failureSubWfTask.getTaskId());
        }

        // then: verify that the sub workflow has failed
        Workflow parentWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowId =
                parentWorkflow.getTaskByRefName("test_task_failed_sub_wf").getSubWorkflowId();
        sweep(subWorkflowId);
        sweep(workflowInstanceId);

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, subWorkflow.getStatus());
        assertEquals(2, subWorkflow.getTasks().size());
        assertTrue(
                subWorkflow
                        .getReasonForIncompletion()
                        .contains("Workflow is FAILED by TERMINATE task"));

        List<Task> subTasks = subWorkflow.getTasks();
        assertEquals(Task.Status.COMPLETED, subTasks.get(0).getStatus());
        assertEquals("LAMBDA", subTasks.get(0).getTaskType());
        assertEquals(1, subTasks.get(0).getSeq());
        assertEquals(Task.Status.COMPLETED, subTasks.get(1).getStatus());
        assertEquals("TERMINATE", subTasks.get(1).getTaskType());
        assertEquals(2, subTasks.get(1).getSeq());

        // then: Verify that the workflow has failed and correct inputs passed into the failure
        // workflow
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        List<Task> tasks = workflow.getTasks();
        assertEquals(Task.Status.COMPLETED, tasks.get(0).getStatus());
        assertEquals("LAMBDA", tasks.get(0).getTaskType());
        assertEquals("lambdaTask1", tasks.get(0).getReferenceTaskName());
        assertEquals(1, tasks.get(0).getSeq());
        assertEquals(Task.Status.FAILED, tasks.get(1).getStatus());
        assertEquals("SUB_WORKFLOW", tasks.get(1).getTaskType());
        assertEquals(2, tasks.get(1).getSeq());

        String failedWorkflowId = (String) workflow.getOutput().get("conductor.failure_workflow");
        String workflowCorrelationId = workflow.getCorrelationId();
        String workflowFailureTaskId = tasks.get(1).getTaskId();

        WorkflowModel failureWorkflow =
                workflowExecutionService.getWorkflowModel(failedWorkflowId, true);
        assertEquals(WorkflowModel.Status.COMPLETED, failureWorkflow.getStatus());
        assertEquals(workflowCorrelationId, failureWorkflow.getCorrelationId());
        assertEquals(workflowInstanceId, failureWorkflow.getInput().get("workflowId"));
        assertEquals(workflowFailureTaskId, failureWorkflow.getInput().get("failureTaskId"));
        assertEquals(1, failureWorkflow.getTasks().size());
        assertEquals("LAMBDA", failureWorkflow.getTasks().get(0).getTaskType());
        assertNotNull(failureWorkflow.getInput().get("failedWorkflow"));
    }
}
