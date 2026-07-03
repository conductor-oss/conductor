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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;
import com.netflix.conductor.test.utils.MockExternalPayloadStorage;
import com.netflix.conductor.test.utils.UserTask;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPayload;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedLargePayloadTask;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class ExternalPayloadStorageTest extends AbstractSpecification {

    private static final String LINEAR_WORKFLOW_T1_T2 = "integration_test_wf";
    private static final String CONDITIONAL_SYSTEM_TASK_WORKFLOW = "ConditionalSystemWorkflow";
    private static final String FORK_JOIN_WF = "FanInOutTest";
    private static final String DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest";
    private static final String WORKFLOW_WITH_INLINE_SUB_WF = "WorkflowWithInlineSubWorkflow";
    private static final String WORKFLOW_WITH_DECISION_AND_TERMINATE =
            "ConditionalTerminateWorkflow";
    private static final String WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK =
            "workflow_with_synchronous_system_task";

    @Autowired UserTask userTask;

    @Autowired SubWorkflow subWorkflowTask;

    @Autowired Join joinTask;

    @Autowired MockExternalPayloadStorage mockExternalPayloadStorage;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_workflow_1_integration_test.json",
                "conditional_system_task_workflow_integration_test.json",
                "fork_join_integration_test.json",
                "simple_workflow_with_sub_workflow_inline_def_integration_test.json",
                "decision_and_terminate_integration_test.json",
                "workflow_with_synchronous_system_task.json",
                "dynamic_fork_join_integration_test.json");
    }

    @Test
    @DisplayName("Test simple workflow using external payload storage")
    void testSimpleWorkflowUsingExternalPayloadStorage() {
        // given: An existing simple workflow definition
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        // and: input required to start large payload workflow
        String correlationId = "wf_external_storage";
        String workflowInputPath = uploadInitialWorkflowInput();

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that the 'integration_task1' is complete and the next task is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: poll and complete the 'integration_task_2' with external payload storage
        pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_2", "task2.integration.worker", "");

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // then: verify that the 'integration_task_2' is complete and the workflow is completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
    }

    @Test
    @DisplayName("Test workflow with synchronous system task using external payload storage")
    void testWorkflowWithSynchronousSystemTaskUsingExternalPayloadStorage() {
        // given: An existing workflow definition with sync system task followed by a simple task
        metadataService.getWorkflowDef(WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK, 1);

        // and: input required to start large payload workflow
        String correlationId = "wf_external_storage";
        String workflowInputPath = uploadInitialWorkflowInput();

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(
                        WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK,
                        1,
                        correlationId,
                        null,
                        workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that 'integration_task1' is complete and the workflow is completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        // output of .tp2.TEST_SAMPLE | length expression from output.json.
        // On assertion failure, check workflow definition and output.json
        assertEquals(104, workflow.getTasks().get(1).getOutputData().get("result"));
    }

    @Test
    @DisplayName("Test conditional workflow with system task using external payload storage")
    void testConditionalWorkflowWithSystemTaskUsingExternalPayloadStorage() {
        // given: An existing workflow definition
        metadataService.getWorkflowDef(CONDITIONAL_SYSTEM_TASK_WORKFLOW, 1);

        // and: input required to start large payload workflow
        String workflowInputPath = uploadInitialWorkflowInput();
        String correlationId = "conditional_system_external_storage";

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(
                        CONDITIONAL_SYSTEM_TASK_WORKFLOW,
                        1,
                        correlationId,
                        null,
                        workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that 'integration_task1' is complete and the next task is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());

        // when: the system task 'USER_TASK' is started by issuing a system task call
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String taskId = workflow.getTaskByRefName("user_task").getTaskId();
        asyncSystemTaskExecutor.execute(userTask, taskId);

        // then: verify that the user task is in a COMPLETED state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());
        assertEquals(104, workflow.getTasks().get(2).getOutputData().get("size"));
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());

        // when: poll and complete and 'integration_task_3'
        Task pollAndCompleteTask3 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3",
                        "task3.integration.worker",
                        Map.of("op", "success_task3"));

        // then: verify that the 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3);

        // then: verify that the 'integration_task_3' is complete and the workflow is completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());
        assertEquals(104, workflow.getTasks().get(2).getOutputData().get("size"));
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
    }

    @Test
    @DisplayName("Test fork join workflow using external payload storage")
    void testForkJoinWorkflowUsingExternalPayloadStorage() {
        // given: An existing fork join workflow definition
        metadataService.getWorkflowDef(FORK_JOIN_WF, 1);

        // and: input required to start large payload workflow
        String correlationId = "fork_join_external_storage";
        String workflowInputPath = uploadInitialWorkflowInput();

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_WF, 1, correlationId, null, workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());

        // when: the first task of the left fork is polled and completed
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        Task polledAndAckTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask);

        // and: task is completed and the next task in the fork is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());

        // when: the first task of the right fork is polled and completed with external payload
        // storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task polledAndAckLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_2", "task2.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask);

        // and: task is completed and the workflow is in running state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());

        // when: the second task of the left fork is polled and completed with external payload
        // storage
        polledAndAckLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_3", "task3.integration.worker", taskOutputPath);

        // and: the workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that the 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask);

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: task is completed and the next task after join is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertTrue(workflow.getTasks().get(2).getOutputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertTrue(workflow.getTasks().get(3).getOutputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());
        assertTrue(workflow.getTasks().get(4).getOutputData().isEmpty());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());

        // when: the task 'integration_task_4' is polled and completed
        polledAndAckTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_4", "task4.integration.worker");

        // then: verify that the 'integration_task_4' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask);

        // and: task is completed and the workflow is in completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertTrue(workflow.getTasks().get(2).getOutputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertTrue(workflow.getTasks().get(3).getOutputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());
        assertTrue(workflow.getTasks().get(4).getOutputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
    }

    @Test
    @DisplayName("Test workflow with subworkflow using external payload storage")
    void testWorkflowWithSubworkflowUsingExternalPayloadStorage() {
        // given: An existing workflow definition
        metadataService.getWorkflowDef(WORKFLOW_WITH_INLINE_SUB_WF, 1);

        // and: input required to start large payload workflow
        String workflowInputPath = uploadInitialWorkflowInput();
        String correlationId = "workflow_with_inline_sub_wf_external_storage";

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(
                        WORKFLOW_WITH_INLINE_SUB_WF, 1, correlationId, null, workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // when: the subworkflow is started by issuing a system task call
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        Task inlineSubWfTask =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && Task.Status.SCHEDULED.equals(t.getStatus()))
                        .findFirst()
                        .orElse(null);
        if (inlineSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, inlineSubWfTask.getTaskId());
        }

        // and: verify that 'integration_task1' is complete and the next task is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowTaskId = workflow.getTaskByRefName("swt").getTaskId();

        // then: verify that the sub workflow task is in a IN_PROGRESS state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals(TaskType.SUB_WORKFLOW.name(), workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());

        // when: sub workflow is retrieved
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowInstanceId = workflow.getTaskByRefName("swt").getSubWorkflowId();
        sweep(subWorkflowInstanceId);

        // then: verify that the sub workflow is in a RUNNING state
        Workflow subWorkflow =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertTrue(subWorkflow.getInput().isEmpty());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_3", subWorkflow.getTasks().get(0).getTaskType());

        // when: poll and complete the 'integration_task_3' with external payload storage
        pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_3", "task3.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that the sub workflow is completed
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());
        assertTrue(subWorkflow.getInput().isEmpty());
        assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_3", subWorkflow.getTasks().get(0).getTaskType());
        assertTrue(subWorkflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue(subWorkflow.getOutput().isEmpty());

        // and: the subworkflow task is completed and the workflow is in running state
        sweep(workflowInstanceId);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals(TaskType.SUB_WORKFLOW.name(), workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());
        assertTrue(workflow.getTasks().get(1).getOutputData().isEmpty());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());

        // when: poll and complete the 'integration_task_2' with external payload storage
        pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_2", "task2.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that the task is completed and the workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals(TaskType.SUB_WORKFLOW.name(), workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());
        assertTrue(workflow.getTasks().get(1).getOutputData().isEmpty());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());
        assertTrue(workflow.getTasks().get(2).getOutputData().isEmpty());
    }

    @Test
    @DisplayName("Test retry workflow using external payload storage")
    void testRetryWorkflowUsingExternalPayloadStorage() {
        // setup: Modify the task definition
        TaskDef persistedTask2Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_2").get();
        TaskDef modifiedTask2Definition =
                new TaskDef(
                        persistedTask2Definition.getName(),
                        persistedTask2Definition.getDescription(),
                        persistedTask2Definition.getOwnerEmail(),
                        2,
                        persistedTask2Definition.getTimeoutSeconds(),
                        persistedTask2Definition.getResponseTimeoutSeconds());
        modifiedTask2Definition.setRetryDelaySeconds(0);
        metadataService.updateTaskDef(modifiedTask2Definition);

        try {
            // and: an existing simple workflow definition
            metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

            // and: input required to start large payload workflow
            String correlationId = "retry_wf_external_storage";
            String workflowInputPath = uploadInitialWorkflowInput();

            // when: the workflow is started
            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath);

            // then: verify that the workflow is in a RUNNING state
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: poll and complete the 'integration_task_1' with external payload storage
            String taskOutputPath = uploadLargeTaskOutput();
            Task pollAndCompleteLargePayloadTask =
                    workflowTestUtil.pollAndCompleteLargePayloadTask(
                            "integration_task_1", "task1.integration.worker", taskOutputPath);

            // then: verify that the 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

            // and: verify that 'integration_task1' is complete and the next task is scheduled
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

            // when: poll and fail the 'integration_task_2'
            Task pollAndFailTask2Try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task2.integration.worker", "failed");

            // then: verify that the task is polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndFailTask2Try1);

            // and: verify that task is retried and workflow is still running
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());

            // when: poll and complete the retried 'integration_task_2'
            Task pollAndCompleteTask2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2",
                            "task2.integration.worker",
                            Map.of("op", "success_task2"));

            // then: verify that the task is polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2);

            // and: verify that the workflow is completed
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
            assertTrue(workflow.getOutput().isEmpty());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());

        } finally {
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    @Test
    @DisplayName("Test workflow with terminate in decision branch using external payload storage")
    void testWorkflowWithTerminateInDecisionBranchUsingExternalPayloadStorage() {
        // given: An existing workflow definition
        metadataService.getWorkflowDef(WORKFLOW_WITH_DECISION_AND_TERMINATE, 1);

        // and: input required to start large payload workflow
        String workflowInputPath = uploadInitialWorkflowInput();
        String correlationId = "decision_terminate_external_storage";

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(
                        WORKFLOW_WITH_DECISION_AND_TERMINATE,
                        1,
                        correlationId,
                        null,
                        workflowInputPath);

        // then: verify that the workflow is in RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(1, workflow.getTasks().get(0).getSeq());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = uploadLargeTaskOutput();
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has FAILED due
        // to terminate task
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertTrue(
                workflow.getReasonForIncompletion()
                        .contains("Workflow is FAILED by TERMINATE task"));
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals(1, workflow.getTasks().get(0).getSeq());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(2, workflow.getTasks().get(1).getSeq());
        assertEquals("TERMINATE", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());
        assertEquals(3, workflow.getTasks().get(2).getSeq());
        assertTrue(workflow.getTasks().get(2).getOutputData().isEmpty());
    }

    @Test
    @DisplayName("Test dynamic fork join workflow with subworkflow using external payload storage")
    void testDynamicForkJoinWorkflowWithSubworkflowUsingExternalPayloadStorage() {
        // given: An existing dynamic fork join workflow definition
        metadataService.getWorkflowDef(DYNAMIC_FORK_JOIN_WF, 1);

        // and: input required to start large payload workflow
        String correlationId = "dynamic_fork_join_subworkflow_external_storage";
        String workflowInputPath = uploadInitialWorkflowInput();

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(DYNAMIC_FORK_JOIN_WF, 1, correlationId, null, workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertTrue(workflow.getInput().isEmpty());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the 'integration_task_1' with external payload storage
        String taskOutputPath = UUID.randomUUID() + ".json";
        mockExternalPayloadStorage.upload(
                taskOutputPath, mockExternalPayloadStorage.curateDynamicForkLargePayload());
        Task pollAndCompleteLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_1", "task1.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask);

        // when: the sub workflow system task is executed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        Task dynamicSubWfTask =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && Task.Status.SCHEDULED.equals(t.getStatus()))
                        .findFirst()
                        .orElse(null);
        if (dynamicSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, dynamicSubWfTask.getTaskId());
        }

        // then: verify that workflow has progressed further ahead and new dynamic tasks have been
        // scheduled with externalized payloads
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
        assertTrue(workflow.getTasks().get(1).getInputData().isEmpty());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
        assertTrue(workflow.getTasks().get(2).getInputData().isEmpty());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("dynamicfanouttask_join", workflow.getTasks().get(3).getReferenceTaskName());
    }

    @Test
    @DisplayName("Test update task output multiple times using external payload storage")
    void testUpdateTaskOutputMultipleTimesUsingExternalPayloadStorage() {
        // given: An existing simple workflow definition
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(
                        LINEAR_WORKFLOW_T1_T2,
                        1,
                        "multi_task_update_external_storage",
                        new HashMap<>(),
                        null);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and update 'integration_task_1' with external payload storage output
        String taskOutputPath = uploadLargeTaskOutput();
        workflowTestUtil.pollAndUpdateTask(
                "integration_task_1", "task1.integration.worker", taskOutputPath, null, 1);

        // then: verify that 'integration_task1's output is updated correctly
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals(
                taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());

        // when: poll and update 'integration_task_1' with no additional output
        workflowTestUtil.pollAndUpdateTask(
                "integration_task_1", "task1.integration.worker", null, null, 1);

        // then: verify that 'integration_task1's output is updated correctly (no duplicate upload)
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        // no duplicate upload
        assertEquals(
                taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());

        // when: poll and complete 'integration_task_1' with additional output
        Map<String, Object> output = new HashMap<>();
        output.put("k1", "v1");
        output.put("k2", "v2");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", output, 1);

        // then: verify that 'integration_task1 is complete and output is updated correctly
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        // upload again with additional output
        assertNotEquals(
                taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        verifyPayload(
                output,
                mockExternalPayloadStorage.downloadPayload(
                        workflow.getTasks().get(0).getExternalOutputPayloadStoragePath()));
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: poll and update 'integration_task_2' with output
        Map<String, Object> output1 = new HashMap<>();
        output1.put("k1", "v1");
        output1.put("k2", "v2");
        workflowTestUtil.pollAndUpdateTask(
                "integration_task_2", "task1.integration.worker", null, output1, 1);

        // then: verify that 'integration_task2's output is updated correctly
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertNull(workflow.getTasks().get(1).getExternalOutputPayloadStoragePath());
        verifyPayload(output1, workflow.getTasks().get(1).getOutputData());

        // when: poll and complete 'integration_task_2' with additional output
        Map<String, Object> output2 = new HashMap<>();
        output2.put("k3", "v3");
        output2.put("k4", "v4");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", output2, 1);

        // then: verify that 'integration_task2 is complete and output is updated correctly
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertNull(workflow.getTasks().get(1).getExternalOutputPayloadStoragePath());
        output1.putAll(output2);
        verifyPayload(output1, workflow.getTasks().get(1).getOutputData());
    }

    @Test
    @DisplayName(
            "Test fork join workflow exceed external storage limit should fail the task and workflow")
    void testForkJoinWorkflowExceedExternalStorageLimitShouldFailTaskAndWorkflow() {
        // given: An existing fork join workflow definition
        metadataService.getWorkflowDef(FORK_JOIN_WF, 1);

        // and: input required to start large payload workflow
        String correlationId = "fork_join_external_storage";
        String workflowInputPath = uploadInitialWorkflowInput();

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_WF, 1, correlationId, null, workflowInputPath);

        // then: verify that the workflow is in a RUNNING state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());

        // when: the first task of the left fork is polled and completed
        Task polledAndAckTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask);

        // and: task is completed and the next task in the fork is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());

        // when: the first task of the right fork is polled and completed with external payload
        // storage (exceeds limit)
        String taskOutputPath = UUID.randomUUID() + ".json";
        mockExternalPayloadStorage.upload(
                taskOutputPath, mockExternalPayloadStorage.createLargePayload(500));
        Task polledAndAckLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_2", "task2.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask);

        // and: task is completed and the workflow is in running state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(4).getTaskType());

        // when: the second task of the left fork is polled and completed with external payload
        // storage (exceeds limit)
        taskOutputPath = UUID.randomUUID() + ".json";
        mockExternalPayloadStorage.upload(
                taskOutputPath, mockExternalPayloadStorage.createLargePayload(500));
        polledAndAckLargePayloadTask =
                workflowTestUtil.pollAndCompleteLargePayloadTask(
                        "integration_task_3", "task3.integration.worker", taskOutputPath);

        // then: verify that the 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask);

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: task is completed and the join task is failed because of exceeding size limit
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertTrue(workflow.getTasks().get(2).getOutputData().isEmpty());
        assertEquals(
                Task.Status.FAILED_WITH_TERMINAL_ERROR, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertTrue(workflow.getTasks().get(3).getOutputData().isEmpty());
        assertNull(workflow.getTasks().get(3).getExternalOutputPayloadStoragePath());
    }

    private String uploadLargeTaskOutput() {
        String taskOutputPath = UUID.randomUUID() + ".json";
        mockExternalPayloadStorage.upload(
                taskOutputPath, mockExternalPayloadStorage.readOutputDotJson(), 0);
        return taskOutputPath;
    }

    private String uploadInitialWorkflowInput() {
        String workflowInputPath = UUID.randomUUID() + ".json";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("case", "two");
        mockExternalPayloadStorage.upload(workflowInputPath, input);
        return workflowInputPath;
    }
}
