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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class SubWorkflowTest extends AbstractSpecification {

    private static final String WORKFLOW_WITH_SUBWORKFLOW = "integration_test_wf_with_sub_wf";
    private static final String SUB_WORKFLOW = "sub_workflow";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired SubWorkflow subWorkflowTask;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_one_task_sub_workflow_integration_test.json",
                "simple_workflow_1_integration_test.json",
                "workflow_with_sub_workflow_1_integration_test.json");
    }

    @Test
    @DisplayName(
            "Test retrying a subworkflow where parent workflow timed out due to workflowTimeout")
    void testRetryingSubworkflowWhereParentWorkflowTimedOutDueToWorkflowTimeout() throws Exception {
        // setup: Register a workflow definition with a timeout policy set to timeout workflow
        WorkflowDef persistedWorkflowDefinition =
                metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1);
        WorkflowDef modifiedWorkflowDefinition = new WorkflowDef();
        modifiedWorkflowDefinition.setName(persistedWorkflowDefinition.getName());
        modifiedWorkflowDefinition.setVersion(persistedWorkflowDefinition.getVersion());
        modifiedWorkflowDefinition.setTasks(persistedWorkflowDefinition.getTasks());
        modifiedWorkflowDefinition.setInputParameters(
                persistedWorkflowDefinition.getInputParameters());
        modifiedWorkflowDefinition.setOutputParameters(
                persistedWorkflowDefinition.getOutputParameters());
        modifiedWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        modifiedWorkflowDefinition.setTimeoutSeconds(10);
        modifiedWorkflowDefinition.setOwnerEmail(persistedWorkflowDefinition.getOwnerEmail());
        metadataService.updateWorkflowDef(List.of(modifiedWorkflowDefinition));

        try {
            // and: verify existing workflow definitions
            metadataService.getWorkflowDef(SUB_WORKFLOW, 1);
            metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1);

            // and: input required to start the workflow execution
            String correlationId = "wf_with_subwf_test_1";
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");
            input.put("subwf", "sub_workflow");

            // when: the workflow is started
            String workflowInstanceId =
                    startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, correlationId, input, null);

            // then: verify that the workflow is in a RUNNING state
            Workflow wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(1, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());

            // when: poll and complete the integration_task_1 task
            Map<String, Object> task1Output = new HashMap<>();
            task1Output.put("op", "task1.done");
            Task pollAndCompleteTask =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker", task1Output);

            // then: verify that the 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

            // when: the subworkflow task is started by issuing a system task call
            Task rootSubWfTask =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTasks()
                            .stream()
                            .filter(
                                    t ->
                                            t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                    && t.getStatus() == Task.Status.SCHEDULED)
                            .findFirst()
                            .orElse(null);
            if (rootSubWfTask != null) {
                asyncSystemTaskExecutor.execute(subWorkflowTask, rootSubWfTask.getTaskId());
            }

            // then: verify that 'integration_task_1' is complete and next task (subworkflow) is
            // IN_PROGRESS
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

            // when: the subworkflow task id is captured
            String subworkflowTaskId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTasks()
                            .get(1)
                            .getTaskId();

            // then: verify that the 'sub_workflow_task' is in a IN_PROGRESS state
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TaskType.SUB_WORKFLOW.name(), wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

            // when: subworkflow is retrieved
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            String subWorkflowId = workflow.getTasks().get(1).getSubWorkflowId();
            sweep(subWorkflowId);

            // then: verify that the sub workflow is RUNNING, and first task is in SCHEDULED state
            Workflow subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
            assertEquals(1, subWf.getTasks().size());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(0).getStatus());

            // when: a delay of 10 seconds is introduced and the workflow is swept to run the
            // evaluation
            Thread.sleep(10000);
            sweep(workflowInstanceId);

            // then: ensure that the workflow has been TIMED OUT and subworkflow task is CANCELED
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TaskType.SUB_WORKFLOW.name(), wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.CANCELED, wf.getTasks().get(1).getStatus());

            // and: ensure that the subworkflow is TERMINATED and task is CANCELED
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.TERMINATED, subWf.getStatus());
            assertEquals(1, subWf.getTasks().size());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, subWf.getTasks().get(0).getStatus());

            // when: the subworkflow is retried
            workflowExecutor.retry(subWorkflowId, false);

            // then: ensure that the subworkflow is RUNNING and task is retried
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
            assertEquals(2, subWf.getTasks().size());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, subWf.getTasks().get(0).getStatus());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(1).getStatus());

            // and: verify that the parent workflow is resumed with the subworkflow task back in
            // progress
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow status =
                                        workflowExecutionService.getExecutionStatus(
                                                workflowInstanceId, true);
                                assertEquals(Workflow.WorkflowStatus.RUNNING, status.getStatus());
                                assertEquals(2, status.getTasks().size());
                                assertEquals(
                                        TaskType.SUB_WORKFLOW.name(),
                                        status.getTasks().get(1).getTaskType());
                                assertEquals(
                                        Task.Status.IN_PROGRESS,
                                        status.getTasks().get(1).getStatus());
                            });

            // when: Polled for simple_task_in_sub_wf task in subworkflow
            Map<String, Object> subTaskOutput = new HashMap<>();
            subTaskOutput.put("op", "simple_task_in_sub_wf.done");
            pollAndCompleteTask =
                    workflowTestUtil.pollAndCompleteTask(
                            "simple_task_in_sub_wf", "task1.integration.worker", subTaskOutput);

            // then: verify that the 'simple_task_in_sub_wf' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

            // and: verify that the subworkflow is in a completed state
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf.getStatus());
            assertEquals(2, subWf.getTasks().size());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, subWf.getTasks().get(0).getStatus());
            assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(1).getStatus());

            // and: subworkflow task is in a completed state
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task subWfTask =
                                        workflowExecutionService.getTask(subworkflowTaskId);
                                assertEquals(Task.Status.COMPLETED, subWfTask.getStatus());
                            });

            // and: the parent workflow is swept
            sweep(workflowInstanceId);

            // and: the parent workflow has been resumed
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TaskType.SUB_WORKFLOW.name(), wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(1).getStatus());
            assertFalse(wf.getTasks().get(1).isSubworkflowChanged());
            assertEquals("simple_task_in_sub_wf.done", wf.getOutput().get("op"));

        } finally {
            // cleanup: Ensure that the changes to the workflow def are reverted
            metadataService.updateWorkflowDef(List.of(persistedWorkflowDefinition));
        }
    }

    @Test
    @DisplayName("Test terminating a subworkflow terminates parent workflow")
    void testTerminatingSubworkflowTerminatesParentWorkflow() {
        // given: Existing workflow and subworkflow definitions
        metadataService.getWorkflowDef(SUB_WORKFLOW, 1);
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1);

        // and: input required to start the workflow execution
        String correlationId = "wf_with_subwf_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        input.put("subwf", "sub_workflow");

        // when: Start a workflow with subworkflow based on the registered definition
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, correlationId, input, null);

        // then: verify that the workflow is in a running state
        Workflow wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(1, wf.getTasks().size());
        assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());

        // when: Polled for integration_task_1 task
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("op", "task1.done");
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // when: the subworkflow task is started by issuing a system task call
        Task midSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }

        // then: verify that 'integration_task1' is complete and next task (subworkflow) is
        // IN_PROGRESS
        wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(2, wf.getTasks().size());
        assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

        // when: subworkflow is retrieved
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowId = workflow.getTasks().get(1).getSubWorkflowId();
        sweep(subWorkflowId);

        // then: verify that the 'sub_workflow_task' is polled and IN_PROGRESS
        wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(2, wf.getTasks().size());
        assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

        // and: verify that the sub workflow is RUNNING, and first task is in SCHEDULED state
        Workflow subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
        assertEquals(1, subWf.getTasks().size());
        assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(0).getStatus());

        // when: subworkflow is terminated
        String terminateReason = "terminating from a test case";
        workflowExecutor.terminateWorkflow(subWorkflowId, terminateReason);

        // then: verify that sub workflow is in terminated state
        subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, subWf.getStatus());
        assertEquals(1, subWf.getTasks().size());
        assertEquals("simple_task_in_sub_wf", subWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, subWf.getTasks().get(0).getStatus());
        assertEquals(terminateReason, subWf.getReasonForIncompletion());

        // and: sweep the parent workflow
        sweep(workflowInstanceId);

        // and: verify that parent workflow is in terminated state
        wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, wf.getStatus());
        assertEquals(2, wf.getTasks().size());
        assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.CANCELED, wf.getTasks().get(1).getStatus());
        assertNotNull(wf.getReasonForIncompletion());
        assertTrue(wf.getReasonForIncompletion().contains(terminateReason));
    }

    @Test
    @DisplayName("Test retrying a workflow with subworkflow resume")
    void testRetryingWorkflowWithSubworkflowResume() {
        // setup: Modify task definition to 0 retries
        TaskDef persistedTask2Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_2").get();
        TaskDef modifiedTask2Definition =
                new TaskDef(
                        persistedTask2Definition.getName(),
                        persistedTask2Definition.getDescription(),
                        persistedTask2Definition.getOwnerEmail(),
                        0,
                        persistedTask2Definition.getTimeoutSeconds(),
                        persistedTask2Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTask2Definition);

        try {
            // and: verify existing workflow definitions
            metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1);
            metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1);

            // and: input required to start the workflow execution
            String correlationId = "wf_retry_with_subwf_resume_test";
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");
            input.put("subwf", "integration_test_wf");

            // when: the workflow is started
            String workflowInstanceId =
                    startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, correlationId, input, null);

            // then: verify that the workflow is in a RUNNING state
            Workflow wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(1, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());

            // when: poll and complete the integration_task_1 task
            Map<String, Object> task1Output = new HashMap<>();
            task1Output.put("op", "task1.done");
            Task pollAndCompleteTask =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker", task1Output);

            // then: verify that the 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

            // when: the subworkflow task is started by issuing a system task call
            Task resumeSubWfTask =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTasks()
                            .stream()
                            .filter(
                                    t ->
                                            t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                    && t.getStatus() == Task.Status.SCHEDULED)
                            .findFirst()
                            .orElse(null);
            if (resumeSubWfTask != null) {
                asyncSystemTaskExecutor.execute(subWorkflowTask, resumeSubWfTask.getTaskId());
            }

            // then: verify that 'integration_task_1' is complete and next task (subworkflow) is
            // IN_PROGRESS
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

            // then: verify that the 'sub_workflow_task' is in a IN_PROGRESS state
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TaskType.SUB_WORKFLOW.name(), wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

            // when: subworkflow is retrieved
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            String subWorkflowId = workflow.getTasks().get(1).getSubWorkflowId();
            sweep(subWorkflowId);

            // then: verify that the sub workflow is RUNNING, and first task is in SCHEDULED state
            Workflow subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
            assertEquals(1, subWf.getTasks().size());
            assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(0).getStatus());

            // when: poll and complete the integration_task_1 task (inside subworkflow)
            task1Output = new HashMap<>();
            task1Output.put("op", "task1.done");
            pollAndCompleteTask =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker", task1Output);

            // then: verify that the 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

            // and: verify that 'integration_task_1' is complete and next task is in SCHEDULED state
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
            assertEquals(2, subWf.getTasks().size());
            assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(1).getStatus());

            // when: poll and fail the integration_task_2 task
            Task pollAndFailTask =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task2.integration.worker", "failed");

            // then: verify that the 'integration_task_2' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndFailTask);

            // then: the sub workflow ends up in a FAILED state
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, subWf.getStatus());
            assertEquals(2, subWf.getTasks().size());
            assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWf.getTasks().get(1).getStatus());

            // and: sweep the parent workflow
            sweep(workflowInstanceId);

            // and: the workflow is in a FAILED state
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals("SUB_WORKFLOW", wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, wf.getTasks().get(1).getStatus());

            // when: the workflow is retried by resuming subworkflow task
            workflowExecutor.retry(workflowInstanceId, true);

            // then: the subworkflow is in a RUNNING state
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
            assertEquals(3, subWf.getTasks().size());
            assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWf.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(2).getStatus());

            // and: the workflow is in a RUNNING state
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());

            // when: poll and complete the integration_task_2 task
            Map<String, Object> task2Output = new HashMap<>();
            task2Output.put("op", "task2.done");
            pollAndCompleteTask =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task2.integration.worker", task2Output);

            // then: verify that the 'integration_task_2' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

            // then: the integration_task_2 is complete sub workflow ends up in a COMPLETED state
            subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf.getStatus());
            assertEquals(3, subWf.getTasks().size());
            assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWf.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", subWf.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWf.getTasks().get(2).getStatus());

            // and: sweep the parent workflow
            sweep(workflowInstanceId);

            // then: the workflow is in a COMPLETED state
            wf = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
            assertEquals(2, wf.getTasks().size());
            assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
            assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(1).getStatus());
            assertFalse(wf.getTasks().get(1).isSubworkflowChanged());

        } finally {
            // cleanup: Ensure that changes to the task def are reverted
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }
}
