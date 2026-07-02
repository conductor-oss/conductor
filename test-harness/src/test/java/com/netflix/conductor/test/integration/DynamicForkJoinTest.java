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
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class DynamicForkJoinTest extends AbstractSpecification {

    @Autowired Join joinTask;

    @Autowired SubWorkflow subWorkflowTask;

    private static final String DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "dynamic_fork_join_integration_test.json",
                "simple_workflow_3_integration_test.json");
    }

    @Test
    @DisplayName("Test dynamic fork join success flow")
    void testDynamicForkJoinSuccessFlow() {
        // when: a dynamic fork join workflow is started
        String workflowInstanceId =
                startWorkflow(
                        DYNAMIC_FORK_JOIN_WF,
                        1,
                        "dynamic_fork_join_workflow",
                        new HashMap<>(),
                        null);

        // then: verify that the workflow has been successfully started and the first task is in
        // scheduled state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the first task 'integration_task_1' output has a list of dynamic tasks
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", Map.of("k1", "v1"));
        dynamicTasksInput.put("xdt2", Map.of("k2", "v2"));

        // and: The 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
        task1Output.put("dynamicTasksInput", dynamicTasksInput);
        Task pollAndCompleteTask1Try =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.worker", task1Output);

        // then: verify that the task was completed
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try);

        // and: verify that workflow has progressed further ahead and new dynamic tasks have been
        // scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
        assertEquals("dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

        // when: Poll and complete 'integration_task_2' and 'integration_task_3'
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("dynamicfanouttask_join")
                        .getTaskId();
        Task pollAndCompleteTask2Try =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.worker", Map.of("ok1", "ov1"));
        Task pollAndCompleteTask3Try =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "task3.worker", Map.of("ok1", "ov1"));

        // and: workflow is evaluated by the reconciler
        sweep(workflowInstanceId);

        // then: verify that the tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try, Map.of("k1", "v1"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try, Map.of("k2", "v2"));

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: verify that the workflow has progressed and the tasks are complete
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(
                List.of("xdt1", "xdt2"), workflow.getTasks().get(4).getInputData().get("joinOn"));
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
        assertEquals(
                "ov1",
                ((Map<?, ?>) workflow.getTasks().get(4).getOutputData().get("xdt1")).get("ok1"));
        assertEquals(
                "ov1",
                ((Map<?, ?>) workflow.getTasks().get(4).getOutputData().get("xdt2")).get("ok1"));
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // when: Poll and complete 'integration_task_4'
        Task pollAndCompleteTask4Try =
                workflowTestUtil.pollAndCompleteTask("integration_task_4", "task4.worker");

        // then: verify that the tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try);

        // and: verify that the workflow is complete
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
    }

    @Test
    @DisplayName("Test dynamic fork join failure of dynamic forked task flow")
    void testDynamicForkJoinFailureOfDynamicForkedTaskFlow() {
        // setup: Make sure that the integration_task_2 does not have any retry count
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
            // when: a dynamic fork join workflow is started
            String workflowInstanceId =
                    startWorkflow(
                            DYNAMIC_FORK_JOIN_WF,
                            1,
                            "dynamic_fork_join_workflow",
                            new HashMap<>(),
                            null);

            // then: verify that the workflow has been successfully started and the first task is in
            // scheduled state
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: the first task 'integration_task_1' output has a list of dynamic tasks
            WorkflowTask workflowTask2 = new WorkflowTask();
            workflowTask2.setName("integration_task_2");
            workflowTask2.setTaskReferenceName("xdt1");

            WorkflowTask workflowTask3 = new WorkflowTask();
            workflowTask3.setName("integration_task_3");
            workflowTask3.setTaskReferenceName("xdt2");

            Map<String, Object> dynamicTasksInput = new HashMap<>();
            dynamicTasksInput.put("xdt1", Map.of("k1", "v1"));
            dynamicTasksInput.put("xdt2", Map.of("k2", "v2"));

            // and: The 'integration_task_1' is polled and completed
            Map<String, Object> task1Output = new HashMap<>();
            task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
            task1Output.put("dynamicTasksInput", dynamicTasksInput);
            Task pollAndCompleteTask1Try =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.worker", task1Output);

            // then: verify that the task was completed
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try);

            // and: verify that workflow has progressed further ahead and new dynamic tasks have
            // been scheduled
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: Poll and fail 'integration_task_2'
            Task pollAndCompleteTask2Try =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task2.worker", "it is a failure..");

            // and: workflow is evaluated by the reconciler
            sweep(workflowInstanceId);

            // then: verify that the tasks were polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try, Map.of("k1", "v1"));

            // and: verify that the workflow is in failed state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(
                    List.of("xdt1", "xdt2"),
                    workflow.getTasks().get(4).getInputData().get("joinOn"));
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
        } finally {
            // cleanup: roll back the change made to integration_task_2 definition
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    @Test
    @DisplayName("Retry a failed dynamic fork join workflow")
    void retryAFailedDynamicForkJoinWorkflow() {
        // setup: Make sure that the integration_task_2 does not have any retry count
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
            // when: a dynamic fork join workflow is started
            String workflowInstanceId =
                    startWorkflow(
                            DYNAMIC_FORK_JOIN_WF,
                            1,
                            "dynamic_fork_join_workflow",
                            new HashMap<>(),
                            null);

            // then: verify that the workflow has been successfully started and the first task is in
            // scheduled state
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: the first task 'integration_task_1' output has a list of dynamic tasks
            WorkflowTask workflowTask2 = new WorkflowTask();
            workflowTask2.setName("integration_task_2");
            workflowTask2.setTaskReferenceName("xdt1");

            WorkflowTask workflowTask3 = new WorkflowTask();
            workflowTask3.setName("integration_task_3");
            workflowTask3.setTaskReferenceName("xdt2");

            Map<String, Object> dynamicTasksInput = new HashMap<>();
            dynamicTasksInput.put("xdt1", Map.of("k1", "v1"));
            dynamicTasksInput.put("xdt2", Map.of("k2", "v2"));

            // and: The 'integration_task_1' is polled and completed
            Map<String, Object> task1Output = new HashMap<>();
            task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
            task1Output.put("dynamicTasksInput", dynamicTasksInput);
            Task pollAndCompleteTask1Try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.worker", task1Output);

            // then: verify that the task was completed
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

            // and: verify that workflow has progressed further ahead and new dynamic tasks have
            // been scheduled
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: Poll and fail 'integration_task_2'
            String joinTaskId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("dynamicfanouttask_join")
                            .getTaskId();
            Task pollAndCompleteTask2Try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task2.worker", "it is a failure..");

            // and: workflow is evaluated by the reconciler
            sweep(workflowInstanceId);

            // then: verify that the tasks were polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1, Map.of("k1", "v1"));

            // and: verify that the workflow is in failed state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(
                    List.of("xdt1", "xdt2"),
                    workflow.getTasks().get(4).getInputData().get("joinOn"));
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: The workflow is retried
            workflowExecutor.retry(workflowInstanceId, false);

            // then: verify that the workflow is in running state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(7, workflow.getTasks().size());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(
                    List.of("xdt1", "xdt2"),
                    workflow.getTasks().get(4).getInputData().get("joinOn"));
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());

            // when: Poll and complete 'integration_task_2' and 'integration_task_3'
            Task pollAndCompleteTask2Try2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task2.worker", Map.of("ok1", "ov1"));
            Task pollAndCompleteTask3Try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_3", "task3.worker", Map.of("ok1", "ov1"));

            // and: workflow is evaluated by the reconciler
            sweep(workflowInstanceId);

            // then: verify that the tasks were polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try2, Map.of("k1", "v1"));
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1, Map.of("k2", "v2"));

            // when: JOIN task is polled and executed
            asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

            // then: verify that the workflow has progressed and tasks are complete
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(8, workflow.getTasks().size());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(
                    List.of("xdt1", "xdt2"),
                    workflow.getTasks().get(4).getInputData().get("joinOn"));
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
            assertEquals(
                    "ov1",
                    ((Map<?, ?>) workflow.getTasks().get(4).getOutputData().get("xdt1"))
                            .get("ok1"));
            assertEquals(
                    "ov1",
                    ((Map<?, ?>) workflow.getTasks().get(4).getOutputData().get("xdt2"))
                            .get("ok1"));
            assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
            assertEquals("integration_task_3", workflow.getTasks().get(6).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
            assertEquals("integration_task_4", workflow.getTasks().get(7).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(7).getStatus());

            // when: Poll and complete 'integration_task_4'
            Task pollAndCompleteTask4Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_4", "task4.worker");

            // then: verify that the tasks were polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try1);

            // and: verify that the workflow is complete
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(8, workflow.getTasks().size());
            assertEquals("integration_task_4", workflow.getTasks().get(7).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(7).getStatus());
        } finally {
            // cleanup: roll back the change made to integration_task_2 definition
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    @Test
    @DisplayName("Retry a failed dynamic fork join workflow with forked subworkflow")
    void retryAFailedDynamicForkJoinWorkflowWithForkedSubworkflow() {
        // setup: Make sure that the integration_task_2 does not have any retry count
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
            // when: the dynamic fork join workflow is started
            String workflowInstanceId =
                    startWorkflow(
                            DYNAMIC_FORK_JOIN_WF,
                            1,
                            "dynamic_fork_join_wf_subwf",
                            new HashMap<>(),
                            null);

            // then: verify that the workflow is started and first task is in SCHEDULED state
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            // when: the first task's output has a list of dynamically forked tasks including a
            // subworkflow
            WorkflowTask workflowTask2 = new WorkflowTask();
            workflowTask2.setName("sub_wf_task");
            workflowTask2.setTaskReferenceName("xdt1");
            workflowTask2.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
            SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
            subWorkflowParams.setName("integration_test_wf3");
            subWorkflowParams.setVersion(1);
            workflowTask2.setSubWorkflowParam(subWorkflowParams);

            WorkflowTask workflowTask3 = new WorkflowTask();
            workflowTask3.setName("integration_task_10");
            workflowTask3.setTaskReferenceName("xdt10");

            Map<String, Object> dynamicTasksInput = new HashMap<>();
            dynamicTasksInput.put("xdt1", Map.of("p1", "q1", "p2", "q2"));
            dynamicTasksInput.put("xdt10", Map.of("k2", "v2"));

            // and: The 'integration_task_1' is polled and completed
            Map<String, Object> task1Output = new HashMap<>();
            task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
            task1Output.put("dynamicTasksInput", dynamicTasksInput);
            Task pollAndCompleteTask1Try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.worker", task1Output);

            // then: verify that the task was completed
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

            // and: verify that workflow has progressed further ahead and new dynamic tasks have
            // been scheduled
            // execute the SUB_WORKFLOW system task if it is in SCHEDULED state
            Workflow workflowState =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            Task dynSubWfTask =
                    workflowState.getTasks().stream()
                            .filter(
                                    t ->
                                            "SUB_WORKFLOW".equals(t.getTaskType())
                                                    && Task.Status.SCHEDULED.equals(t.getStatus()))
                            .findFirst()
                            .orElse(null);
            if (dynSubWfTask != null) {
                asyncSystemTaskExecutor.execute(subWorkflowTask, dynSubWfTask.getTaskId());
            }
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: the subworkflow is started by issuing a system task call
            String joinTaskId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("dynamicfanouttask_join")
                            .getTaskId();

            // then: verify that the sub workflow task is in a IN_PROGRESS state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: subworkflow is retrieved
            Workflow parentWorkflowState =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            String subWorkflowId = parentWorkflowState.getTasks().get(2).getSubWorkflowId();
            sweep(subWorkflowId);

            // then: verify that the sub workflow is RUNNING, and first task is in SCHEDULED state
            Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
            assertEquals(1, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

            // and: The 'integration_task_10' is polled and completed
            Task pollAndCompleteTask10Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_10", "task10.worker");

            // then: verify that the task was completed
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask10Try1);

            // and: verify that the workflow is updated
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: The task within sub workflow is polled and completed
            pollAndCompleteTask1Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_1", "task1.worker");

            // then: verify that the task was completed
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

            // and: the next task in the subworkflow is in SCHEDULED state
            subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
            assertEquals(2, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(1).getStatus());

            // when: Poll and fail 'integration_task_2'
            Task pollAndCompleteTask2Try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task2.worker", "failure");

            // and: the workflow is evaluated
            sweep(workflowInstanceId);

            // then: verify that the task was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1);

            // and: the subworkflow is in FAILED state
            subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, subWorkflow.getStatus());
            assertEquals(2, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWorkflow.getTasks().get(1).getStatus());

            // and: the workflow is also in FAILED state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: The workflow is retried
            workflowExecutor.retry(workflowInstanceId, true);

            // then: verify that the workflow is in RUNNING state
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // and: the subworkflow is retried and in RUNNING state
            subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
            assertEquals(3, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWorkflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(2).getStatus());

            // when: the workflow is evaluated
            sweep(workflowInstanceId);

            // then: verify that the JOIN is updated
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(5, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
            assertFalse(workflow.getTasks().get(2).isSubworkflowChanged());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

            // when: Poll and complete 'integration_task_2'
            Task pollAndCompleteTask2Try2 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_2", "task2.worker");

            // then: verify that the task was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try2);

            // and: the sub workflow progressed further
            subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
            assertEquals(4, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWorkflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", subWorkflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(3).getStatus());

            // when: Poll and complete 'integration_task_3'
            Task pollAndCompleteTask3Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_3", "task3.worker");

            // then: verify that the task was polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1);

            // and: the sub workflow is in COMPLETED state
            subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, subWorkflow.getStatus());
            assertEquals(4, subWorkflow.getTasks().size());
            assertEquals("integration_task_1", subWorkflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, subWorkflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", subWorkflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_3", subWorkflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, subWorkflow.getTasks().get(3).getStatus());

            // when: the workflow is evaluated and JOIN task is executed
            sweep(workflowInstanceId);
            asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

            // then: the workflow has progressed beyond the join task
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(6, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertFalse(workflow.getTasks().get(2).isSubworkflowChanged());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
            assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

            // when: Poll and complete 'integration_task_4'
            Task pollAndCompleteTask4Try =
                    workflowTestUtil.pollAndCompleteTask("integration_task_4", "task4.worker");

            // then: verify that the tasks were polled and acknowledged
            verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try);

            // and: verify that the workflow is complete
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(6, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertFalse(workflow.getTasks().get(2).isSubworkflowChanged());
            assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
            assertEquals(
                    "dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
            assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        } finally {
            // cleanup: roll back the change made to integration_task_2 definition
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    @Test
    @DisplayName("Test dynamic fork join empty output")
    void testDynamicForkJoinEmptyOutput() {
        // when: a dynamic fork join workflow is started
        String workflowInstanceId =
                startWorkflow(
                        DYNAMIC_FORK_JOIN_WF,
                        1,
                        "dynamic_fork_join_workflow",
                        new HashMap<>(),
                        null);

        // then: verify that the workflow has been successfully started and the first task is in
        // scheduled state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the first task 'integration_task_1' output has a list of dynamic tasks
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", Map.of("k1", "v1"));
        dynamicTasksInput.put("xdt2", Map.of("k2", "v2"));

        // and: The 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
        task1Output.put("dynamicTasksInput", dynamicTasksInput);
        Task pollAndCompleteTask1Try =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.worker", task1Output);

        // then: verify that the task was completed
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try);

        // and: verify that workflow has progressed further ahead and new dynamic tasks have been
        // scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
        assertEquals("dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());

        // when: Poll and complete 'integration_task_2' and 'integration_task_3' with no output
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("dynamicfanouttask_join")
                        .getTaskId();
        Task pollAndCompleteTask2Try =
                workflowTestUtil.pollAndCompleteTask("integration_task_2", "task2.worker");
        Task pollAndCompleteTask3Try =
                workflowTestUtil.pollAndCompleteTask("integration_task_3", "task3.worker");

        // and: workflow is evaluated by the reconciler
        sweep(workflowInstanceId);

        // then: verify that the tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try, Map.of("k1", "v1"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try, Map.of("k2", "v2"));

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: verify that the workflow has progressed and the tasks are complete
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(
                List.of("xdt1", "xdt2"), workflow.getTasks().get(4).getInputData().get("joinOn"));
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("dynamicfanouttask_join", workflow.getTasks().get(4).getReferenceTaskName());
        assertTrue(workflow.getTasks().get(4).getOutputData().isEmpty());
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // when: Poll and complete 'integration_task_4'
        Task pollAndCompleteTask4Try =
                workflowTestUtil.pollAndCompleteTask("integration_task_4", "task4.worker");

        // then: verify that the tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try);

        // and: verify that the workflow is complete
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("integration_task_4", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
    }

    @Test
    @DisplayName("Test dynamic fork join fail when task input is invalid")
    void testDynamicForkJoinFailWhenTaskInputIsInvalid() {
        // when: a dynamic fork join workflow is started
        String workflowInstanceId =
                startWorkflow(
                        DYNAMIC_FORK_JOIN_WF,
                        1,
                        "dynamic_fork_join_workflow",
                        new HashMap<>(),
                        null);

        // then: verify that the workflow has been successfully started and the first task is in
        // scheduled state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the first task 'integration_task_1' output has a list of dynamic tasks with invalid
        // input
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");

        // invalid: values are plain strings, not Maps
        Map<String, Object> invalidDynamicTasksInput = new HashMap<>();
        invalidDynamicTasksInput.put("xdt1", "v1");
        invalidDynamicTasksInput.put("xdt2", "v2");

        // and: The 'integration_task_1' is polled and completed
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
        task1Output.put("dynamicTasksInput", invalidDynamicTasksInput);
        Task pollAndCompleteTask1Try =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.worker", task1Output);

        // then: verify that the task was completed
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try);

        // and: verify that workflow failed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Test dynamic fork join return failed workflow when start with invalid input")
    void testDynamicForkJoinReturnFailedWorkflowWhenStartWithInvalidInput() {
        // when: a dynamic fork join workflow is started with invalid input
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");

        // invalid: values are plain strings, not Maps
        Map<String, Object> invalidDynamicTasksInput = new HashMap<>();
        invalidDynamicTasksInput.put("xdt1", "v1");
        invalidDynamicTasksInput.put("xdt2", "v2");

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("dynamicTasks", List.of(workflowTask2, workflowTask3));
        workflowInput.put("dynamicTasksInput", invalidDynamicTasksInput);

        WorkflowTask dynamicForkJoinTask = new WorkflowTask();
        dynamicForkJoinTask.setName("dynamicfanouttask");
        dynamicForkJoinTask.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinTask.setType("FORK_JOIN_DYNAMIC");
        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put("dynamicTasks", "${workflow.input.dynamicTasks}");
        inputParameters.put("dynamicTasksInput", "${workflow.input.dynamicTasksInput}");
        dynamicForkJoinTask.setInputParameters(inputParameters);
        dynamicForkJoinTask.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinTask.setDynamicForkTasksInputParamName("dynamicTasksInput");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("DynamicForkJoinStartTest");
        workflowDef.setVersion(1);
        workflowDef.getTasks().add(dynamicForkJoinTask);
        workflowDef.setOwnerEmail("test@harness.com");

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName(workflowDef.getName());
        startWorkflowInput.setVersion(workflowDef.getVersion());
        startWorkflowInput.setWorkflowInput(workflowInput);
        startWorkflowInput.setWorkflowDefinition(workflowDef);
        String workflowInstanceId = workflowExecutor.startWorkflow(startWorkflowInput);

        // then: verify that workflow failed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertTrue(workflow.getTasks().isEmpty());
    }
}
