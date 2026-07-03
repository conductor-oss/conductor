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
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class ExclusiveJoinTest extends AbstractSpecification {

    private static final String EXCLUSIVE_JOIN_WF = "ExclusiveJoinTestWorkflow";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows("exclusive_join_integration_test.json");
    }

    private TaskResult setTaskResult(
            String workflowInstanceId,
            String taskId,
            TaskResult.Status status,
            Map<String, Object> output) {
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(taskId);
        taskResult.setWorkflowInstanceId(workflowInstanceId);
        taskResult.setStatus(status);
        taskResult.setOutputData(output);
        return taskResult;
    }

    @Test
    @DisplayName("Test that the default decision is run")
    void testDefaultDecisionIsRun() {
        // given: The input parameter required to make decision_1 is null to ensure that the default
        // decision is run
        Map<String, Object> input = new HashMap<>();
        input.put("decision_1", "null");

        // when: An exclusive join workflow is started with the workflow input
        String workflowInstanceId =
                startWorkflow(EXCLUSIVE_JOIN_WF, 1, "exclusive_join_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("taskReferenceName", "task1");
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has COMPLETED
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(3, completedWorkflow.getTasks().size());
        assertEquals("integration_task_1", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(1).getStatus());
        assertEquals("EXCLUSIVE_JOIN", completedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(2).getStatus());
        assertEquals(
                "task1",
                completedWorkflow.getTasks().get(2).getOutputData().get("taskReferenceName"));
    }

    @Test
    @DisplayName("Test when the one decision is true and the other is decision null")
    void testOneDecisionTrueOtherNull() {
        // given: The input parameter required to make decision_1 true and decision_2 null
        Map<String, Object> input = new HashMap<>();
        input.put("decision_1", "true");
        input.put("decision_2", "null");

        // when: An exclusive join workflow is started with the workflow input
        String workflowInstanceId =
                startWorkflow(EXCLUSIVE_JOIN_WF, 1, "exclusive_join_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("taskReferenceName", "task1");
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow.getStatus());
        assertEquals(3, progressedWorkflow.getTasks().size());
        assertEquals("DECISION", progressedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", progressedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow.getTasks().get(2).getStatus());

        // when: the task 'integration_task_2' is polled and completed
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("taskReferenceName", "task2");
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker", task2Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);

        // and: verify that the 'integration_task_2' is COMPLETED and the workflow has COMPLETED
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(5, completedWorkflow.getTasks().size());
        assertEquals("integration_task_1", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", completedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(2).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(3).getStatus());
        assertEquals("EXCLUSIVE_JOIN", completedWorkflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(4).getStatus());
        assertEquals(
                "task2",
                completedWorkflow.getTasks().get(4).getOutputData().get("taskReferenceName"));
    }

    @Test
    @DisplayName("Test when both the decisions, decision_1 and decision_2 are true")
    void testBothDecisionsTrue() {
        // given: The input parameters to ensure that both the decisions are true
        Map<String, Object> input = new HashMap<>();
        input.put("decision_1", "true");
        input.put("decision_2", "true");

        // when: An exclusive join workflow is started with the workflow input
        String workflowInstanceId =
                startWorkflow(EXCLUSIVE_JOIN_WF, 1, "exclusive_join_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("taskReferenceName", "task1");
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow1 =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow1.getStatus());
        assertEquals(3, progressedWorkflow1.getTasks().size());
        assertEquals("DECISION", progressedWorkflow1.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow1.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", progressedWorkflow1.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow1.getTasks().get(2).getStatus());

        // when: the task 'integration_task_2' is polled and completed
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("taskReferenceName", "task2");
        Task polledAndCompletedTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker", task2Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2);

        // and: verify that the 'integration_task_2' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow2 =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow2.getStatus());
        assertEquals(5, progressedWorkflow2.getTasks().size());
        assertEquals("integration_task_1", progressedWorkflow2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(0).getStatus());
        assertEquals("DECISION", progressedWorkflow2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", progressedWorkflow2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(2).getStatus());
        assertEquals("DECISION", progressedWorkflow2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(3).getStatus());
        assertEquals("integration_task_3", progressedWorkflow2.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow2.getTasks().get(4).getStatus());

        // when: the task 'integration_task_3' is polled and completed
        Map<String, Object> task3Output = new HashMap<>();
        task3Output.put("taskReferenceName", "task3");
        Task polledAndCompletedTask3 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "task3.integration.worker", task3Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask3);

        // and: verify that the 'integration_task_3' is COMPLETED and the workflow has COMPLETED
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(6, completedWorkflow.getTasks().size());
        assertEquals("integration_task_1", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", completedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(2).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_3", completedWorkflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(4).getStatus());
        assertEquals("EXCLUSIVE_JOIN", completedWorkflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(5).getStatus());
        assertEquals(
                "task3",
                completedWorkflow.getTasks().get(5).getOutputData().get("taskReferenceName"));
    }

    @Test
    @DisplayName("Test when decision_1 is false and  decision_3 is default")
    void testDecision1FalseDecision3Default() {
        // given: The input parameter required to make decision_1 false and decision_3 default
        Map<String, Object> input = new HashMap<>();
        input.put("decision_1", "false");
        input.put("decision_3", "null");

        // when: An exclusive join workflow is started with the workflow input
        String workflowInstanceId =
                startWorkflow(EXCLUSIVE_JOIN_WF, 1, "exclusive_join_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("taskReferenceName", "task1");
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow.getStatus());
        assertEquals(3, progressedWorkflow.getTasks().size());
        assertEquals("DECISION", progressedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_4", progressedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow.getTasks().get(2).getStatus());

        // when: the task 'integration_task_4' is polled and completed
        Map<String, Object> task4Output = new HashMap<>();
        task4Output.put("taskReferenceName", "task4");
        Task polledAndCompletedTask4 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_4", "task4.integration.worker", task4Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4);

        // and: verify that the 'integration_task_4' is COMPLETED and the workflow has COMPLETED
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(5, completedWorkflow.getTasks().size());
        assertEquals("integration_task_1", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_4", completedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(2).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(3).getStatus());
        assertEquals("EXCLUSIVE_JOIN", completedWorkflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(4).getStatus());
        assertEquals(
                "task4",
                completedWorkflow.getTasks().get(4).getOutputData().get("taskReferenceName"));
    }

    @Test
    @DisplayName("Test when decision_1 is false and  decision_3 is true")
    void testDecision1FalseDecision3True() {
        // given: The input parameter required to make decision_1 false and decision_3 true
        Map<String, Object> input = new HashMap<>();
        input.put("decision_1", "false");
        input.put("decision_3", "true");

        // when: An exclusive join workflow is started with the workflow input
        String workflowInstanceId =
                startWorkflow(EXCLUSIVE_JOIN_WF, 1, "exclusive_join_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("taskReferenceName", "task1");
        Task polledAndCompletedTask1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", task1Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1);

        // and: verify that the 'integration_task_1' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow1 =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow1.getStatus());
        assertEquals(3, progressedWorkflow1.getTasks().size());
        assertEquals("DECISION", progressedWorkflow1.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow1.getTasks().get(1).getStatus());
        assertEquals("integration_task_4", progressedWorkflow1.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow1.getTasks().get(2).getStatus());

        // when: the task 'integration_task_4' is polled and completed
        Map<String, Object> task4Output = new HashMap<>();
        task4Output.put("taskReferenceName", "task4");
        Task polledAndCompletedTask4 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_4", "task4.integration.worker", task4Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4);

        // and: verify that the 'integration_task_4' is COMPLETED and the workflow has progressed
        Workflow progressedWorkflow2 =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, progressedWorkflow2.getStatus());
        assertEquals(5, progressedWorkflow2.getTasks().size());
        assertEquals("integration_task_1", progressedWorkflow2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(0).getStatus());
        assertEquals("DECISION", progressedWorkflow2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(1).getStatus());
        assertEquals("integration_task_4", progressedWorkflow2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(2).getStatus());
        assertEquals("DECISION", progressedWorkflow2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, progressedWorkflow2.getTasks().get(3).getStatus());
        assertEquals("integration_task_5", progressedWorkflow2.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, progressedWorkflow2.getTasks().get(4).getStatus());

        // when: the task 'integration_task_5' is polled and completed
        Map<String, Object> task5Output = new HashMap<>();
        task5Output.put("taskReferenceName", "task5");
        Task polledAndCompletedTask5 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_5", "task5.integration.worker", task5Output);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask5);

        // and: verify that the 'integration_task_4' is COMPLETED and the workflow has progressed
        Workflow completedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
        assertEquals(6, completedWorkflow.getTasks().size());
        assertEquals("integration_task_1", completedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(0).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_4", completedWorkflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(2).getStatus());
        assertEquals("DECISION", completedWorkflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_5", completedWorkflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(4).getStatus());
        assertEquals("EXCLUSIVE_JOIN", completedWorkflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedWorkflow.getTasks().get(5).getStatus());
        assertEquals(
                "task5",
                completedWorkflow.getTasks().get(5).getOutputData().get("taskReferenceName"));
    }
}
