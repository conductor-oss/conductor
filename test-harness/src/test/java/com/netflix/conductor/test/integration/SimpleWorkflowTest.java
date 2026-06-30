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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class SimpleWorkflowTest extends AbstractSpecification {

    @Autowired QueueDAO queueDAO;

    private static final String LINEAR_WORKFLOW_T1_T2 = "integration_test_wf";
    private static final String INTEGRATION_TEST_WF_NON_RESTARTABLE =
            "integration_test_wf_non_restartable";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_workflow_1_integration_test.json",
                "simple_workflow_with_resp_time_out_integration_test.json");
    }

    @Test
    @DisplayName("Test simple workflow completion")
    void testSimpleWorkflowCompletion() {
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        Task task1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(task1);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        Task task2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");
        verifyPolledAndAcknowledgedTask(task2, Map.of("tp1", inputParam1, "tp2", "task1.done"));

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getOutput().containsKey("o3"));
    }

    @Test
    @DisplayName("Test simple workflow with null inputs")
    void testSimpleWorkflowWithNullInputs() {
        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertTrue(workflowDef.getTasks().get(0).getInputParameters().containsKey("someNullKey"));

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", null);

        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertNull(workflow.getInput().get("param2"));
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertNull(workflow.getTasks().get(0).getInputData().get("someNullKey"));

        Map<String, Object> outputParams = new HashMap<>();
        outputParams.put("someOtherKey", Map.of("a", 1, "A", null));
        outputParams.put("someKey", null);
        Task task1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker", outputParams);
        verifyPolledAndAcknowledgedTask(task1);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("someKey"));
        assertNull(workflow.getTasks().get(0).getOutputData().get("someKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> someOtherKey =
                (Map<String, Object>)
                        workflow.getTasks().get(0).getOutputData().get("someOtherKey");
        assertTrue(someOtherKey.containsKey("A"));
        assertNull(someOtherKey.get("A"));
    }

    @Test
    @DisplayName("Test simple workflow terminal error condition")
    void testSimpleWorkflowTerminalErrorCondition() {
        TaskDef persistedTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTask1Definition =
                new TaskDef(
                        persistedTask1Definition.getName(),
                        persistedTask1Definition.getDescription(),
                        persistedTask1Definition.getOwnerEmail(),
                        1,
                        persistedTask1Definition.getTimeoutSeconds(),
                        persistedTask1Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTask1Definition);

        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        Map<String, Object> outputParameters = workflowDef.getOutputParameters();
        outputParameters.put("validationErrors", "${t1.output.ErrorMessage}");
        metadataService.updateWorkflowDef(workflowDef);

        try {
            String correlationId = "unit_test_1";
            Map<String, Object> input = new HashMap<>();
            input.put("param1", "p1 value");
            input.put("param2", "p2 value");

            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());

            assertThrows(
                    ConflictException.class,
                    () -> workflowExecutor.restart(workflowInstanceId, false));

            Task polledTask1 =
                    workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
            TaskResult taskResult = new TaskResult(polledTask1);
            taskResult.setReasonForIncompletion(
                    "NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down");
            taskResult.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
            taskResult.addOutputData("TERMINAL_ERROR", "Integration endpoint down: FOOBAR");
            taskResult.addOutputData("ErrorMessage", "There was a terminal error");
            workflowExecutionService.updateTask(taskResult);
            sweep(workflowInstanceId);

            assertNotNull(polledTask1);
            assertEquals("integration_task_1", polledTask1.getTaskType());
            assertEquals(workflowInstanceId, polledTask1.getWorkflowInstanceId());

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            Task t1 = workflow.getTaskByRefName("t1");
            assertThat(workflow.getReasonForIncompletion())
                    .contains(
                            "Task "
                                    + t1.getTaskId()
                                    + " failed with status: FAILED and reason: "
                                    + "'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'");
            assertEquals("p1 value", workflow.getOutput().get("o1"));
            assertEquals(
                    "There was a terminal error", workflow.getOutput().get("validationErrors"));
            assertEquals(0, t1.getRetryCount());
            assertEquals(new HashSet<>(List.of("t1")), workflow.getFailedReferenceTaskNames());
            assertEquals(
                    new HashSet<>(List.of("integration_task_1")), workflow.getFailedTaskNames());
        } finally {
            metadataService.updateTaskDef(modifiedTask1Definition);
            outputParameters.remove("validationErrors");
            metadataService.updateWorkflowDef(workflowDef);
        }
    }

    @Test
    @DisplayName("Test Simple Workflow with response timeout")
    void testSimpleWorkflowWithResponseTimeout() throws InterruptedException {
        String correlationId = "unit_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        String workflowInstanceId = startWorkflow("RTOWF", 1, correlationId, workflowInput, null);

        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("task_rt", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(1, queueDAO.getSize("task_rt"));

        Task polledTaskRtTry1 =
                workflowExecutionService.poll("task_rt", "task1.integration.worker.testTimeout");
        assertNotNull(polledTaskRtTry1);
        assertEquals("task_rt", polledTaskRtTry1.getTaskType());
        assertEquals(workflowInstanceId, polledTaskRtTry1.getWorkflowInstanceId());
        assertEquals(Task.Status.IN_PROGRESS, polledTaskRtTry1.getStatus());

        Task noTaskAvailable =
                workflowExecutionService.poll("task_rt", "task1.integration.worker.testTimeout");
        assertNull(noTaskAvailable);

        Thread.sleep(11000);

        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowInstanceId);
                            return queueDAO.getSize("task_rt") == 1;
                        });

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.TIMED_OUT, workflow.getTasks().get(0).getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        Task polledTaskRtTry2 =
                workflowExecutionService.poll("task_rt", "task1.integration.worker.testTimeout");
        polledTaskRtTry2.setCallbackAfterSeconds(2);
        polledTaskRtTry2.setStatus(Task.Status.IN_PROGRESS);
        workflowExecutionService.updateTask(new TaskResult(polledTaskRtTry2));
        assertNotNull(polledTaskRtTry2);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        Thread.sleep(2010);
        queueDAO.processUnacks(polledTaskRtTry2.getTaskDefName());
        workflowExecutor.decide(workflowInstanceId);

        Task task3 =
                workflowTestUtil.pollAndCompleteTask(
                        "task_rt",
                        "task1.integration.worker.testTimeout",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(task3);

        Task task2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task1.integration.worker.testTimeout");
        verifyPolledAndAcknowledgedTask(task2);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @DisplayName(
            "Test if the workflow definitions with and without schema version can be registered")
    void testWorkflowDefinitionSchemaVersion() {
        WorkflowDef workflowDef1 = new WorkflowDef();
        workflowDef1.setName("Test_schema_version1");
        workflowDef1.setVersion(1);
        workflowDef1.setOwnerEmail("test@harness.com");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("integration_task_1");
        workflowTask.setTaskReferenceName("t1");
        workflowDef1.getTasks().add(workflowTask);
        metadataService.updateWorkflowDef(workflowDef1);

        WorkflowDef workflowDef2 = new WorkflowDef();
        workflowDef2.setName("Test_schema_version2");
        workflowDef2.setVersion(1);
        workflowDef2.setSchemaVersion(2);
        workflowDef2.setOwnerEmail("test@harness.com");
        workflowDef2.getTasks().add(workflowTask);
        metadataService.updateWorkflowDef(workflowDef2);

        WorkflowDef foundWorkflowDef1 = metadataService.getWorkflowDef(workflowDef1.getName(), 1);
        WorkflowDef foundWorkflowDef2 = metadataService.getWorkflowDef(workflowDef2.getName(), 1);

        assertNotNull(foundWorkflowDef1);
        assertEquals(2, foundWorkflowDef1.getSchemaVersion());
        assertNotNull(foundWorkflowDef2);
        assertEquals(2, foundWorkflowDef2.getSchemaVersion());
    }

    @Test
    @DisplayName("Test Simple workflow restart without using the latest definition")
    void testSimpleWorkflowRestartWithoutLatestDef() {
        TaskDef persistedTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        persistedTask1Definition.getName(),
                        persistedTask1Definition.getDescription(),
                        persistedTask1Definition.getOwnerEmail(),
                        0,
                        persistedTask1Definition.getTimeoutSeconds(),
                        persistedTask1Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            WorkflowDef workflowDefinition =
                    metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
            assertNotNull(workflowDefinition);
            assertNotNull(workflowDefinition.getFailureWorkflow());
            assertTrue(StringUtils.isNotBlank(workflowDefinition.getFailureWorkflow()));

            String correlationId = "integration_test_1" + UUID.randomUUID().toString();
            Map<String, Object> workflowInput = new HashMap<>();
            String inputParam1 = "p1 value";
            workflowInput.put("param1", inputParam1);
            workflowInput.put("param2", "p2 value");

            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task task1try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.integration.worker", "failed..");
            verifyPolledAndAcknowledgedTask(task1try1);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            workflowExecutor.restart(workflowInstanceId, false);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task task1try2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker");
            verifyPolledAndAcknowledgedTask(task1try2);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            Task task2try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task1.integration.worker");
            verifyPolledAndAcknowledgedTask(task2try1);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        } finally {
            metadataService.updateTaskDef(persistedTask1Definition);
        }
    }

    @Test
    @DisplayName("Test Simple workflow restart with the latest definition")
    void testSimpleWorkflowRestartWithLatestDef() {
        TaskDef persistedTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        persistedTask1Definition.getName(),
                        persistedTask1Definition.getDescription(),
                        persistedTask1Definition.getOwnerEmail(),
                        0,
                        persistedTask1Definition.getTimeoutSeconds(),
                        persistedTask1Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTaskDefinition);

        WorkflowDef workflowDefinition = null;
        try {
            workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
            assertNotNull(workflowDefinition);
            assertNotNull(workflowDefinition.getFailureWorkflow());
            assertTrue(StringUtils.isNotBlank(workflowDefinition.getFailureWorkflow()));

            String correlationId = "integration_test_1" + UUID.randomUUID().toString();
            Map<String, Object> workflowInput = new HashMap<>();
            String inputParam1 = "p1 value";
            workflowInput.put("param1", inputParam1);
            workflowInput.put("param2", "p2 value");

            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task task1try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.integration.worker", "failed..");
            verifyPolledAndAcknowledgedTask(task1try1);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            WorkflowTask newWorkflowTask = new WorkflowTask();
            newWorkflowTask.setName("integration_task_20");
            newWorkflowTask.setTaskReferenceName("task_added");
            newWorkflowTask.setWorkflowTaskType(TaskType.SIMPLE);
            workflowDefinition.getTasks().add(newWorkflowTask);
            workflowDefinition.setVersion(2);
            metadataService.updateWorkflowDef(workflowDefinition);

            workflowExecutor.restart(workflowInstanceId, true);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task task1try2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker");
            verifyPolledAndAcknowledgedTask(task1try2);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            Task task2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task1.integration.worker");
            verifyPolledAndAcknowledgedTask(task2);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task task20 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_20", "task1.integration.worker");
            verifyPolledAndAcknowledgedTask(task20);
            assertEquals(workflowInstanceId, task20.getWorkflowInstanceId());
            assertEquals("task_added", task20.getReferenceTaskName());

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
        } finally {
            metadataService.updateTaskDef(persistedTask1Definition);
            if (workflowDefinition != null) {
                metadataService.unregisterWorkflowDef(workflowDefinition.getName(), 2);
            }
        }
    }

    @Test
    @DisplayName("Test simple workflow with task retries")
    void testSimpleWorkflowWithTaskRetries() {
        TaskDef integrationTask2Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_2").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        integrationTask2Definition.getName(),
                        integrationTask2Definition.getDescription(),
                        integrationTask2Definition.getOwnerEmail(),
                        3,
                        integrationTask2Definition.getTimeoutSeconds(),
                        integrationTask2Definition.getResponseTimeoutSeconds());
        modifiedTaskDefinition.setRetryDelaySeconds(2);
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            String correlationId = "integration_test_1";
            Map<String, Object> workflowInput = new HashMap<>();
            workflowInput.put("param1", "p1 value");
            workflowInput.put("param2", "p2 value");
            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

            assertNotNull(workflowInstanceId);
            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task polledTask1 =
                    workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
            polledTask1.setStatus(Task.Status.COMPLETED);
            String polledTask1Output =
                    "task1.output -> "
                            + polledTask1.getInputData().get("p1")
                            + "."
                            + polledTask1.getInputData().get("p2");
            polledTask1.getOutputData().put("op", polledTask1Output);
            workflowExecutionService.updateTask(new TaskResult(polledTask1));

            assertThat(polledTask1.getInputData()).containsKey("p1");
            assertThat(polledTask1.getInputData()).containsKey("p2");
            assertEquals("p1 value", polledTask1.getInputData().get("p1"));
            assertEquals("p2 value", polledTask1.getInputData().get("p2"));

            Task task2try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2",
                            "task2.integration.worker",
                            "failure...0",
                            null,
                            2);
            verifyPolledAndAcknowledgedTask(
                    task2try1, Map.of("tp2", polledTask1Output, "tp1", "p1 value"));

            Task task2try2 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2",
                            "task2.integration.worker",
                            "failure...0",
                            null,
                            2);
            verifyPolledAndAcknowledgedTask(
                    task2try2, Map.of("tp2", polledTask1Output, "tp1", "p1 value"));

            Task task2try3 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task2.integration.worker");
            verifyPolledAndAcknowledgedTask(
                    task2try3, Map.of("tp2", polledTask1Output, "tp1", "p1 value"));

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(4, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals(
                    workflow.getTasks().get(1).getTaskId(),
                    workflow.getTasks().get(2).getRetriedTaskId());
            assertEquals(
                    workflow.getTasks().get(2).getTaskId(),
                    workflow.getTasks().get(3).getRetriedTaskId());
            assertEquals(new HashSet<>(List.of("t2")), workflow.getFailedReferenceTaskNames());
            assertEquals(
                    new HashSet<>(List.of("integration_task_2")), workflow.getFailedTaskNames());
        } finally {
            metadataService.updateTaskDef(integrationTask2Definition);
        }
    }

    @Test
    @DisplayName("Test simple workflow with retry at workflow level")
    void testSimpleWorkflowWithRetryAtWorkflowLevel() {
        TaskDef integrationTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        integrationTask1Definition.getName(),
                        integrationTask1Definition.getDescription(),
                        integrationTask1Definition.getOwnerEmail(),
                        1,
                        integrationTask1Definition.getTimeoutSeconds(),
                        integrationTask1Definition.getResponseTimeoutSeconds());
        modifiedTaskDefinition.setRetryDelaySeconds(0);
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            String correlationId = "retry_test" + UUID.randomUUID().toString();
            Map<String, Object> workflowInput = new HashMap<>();
            String inputParam1 = "p1 value";
            workflowInput.put("param1", inputParam1);
            workflowInput.put("param2", "p2 value");

            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

            assertNotNull(workflowInstanceId);
            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
            assertEquals(
                    workflow.getTasks().get(0).getTaskId(),
                    workflow.getTasks().get(0).getInputData().get("p3"));

            WorkflowDef wfDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
            assertNotNull(wfDef.getFailureWorkflow());
            assertTrue(StringUtils.isNotBlank(wfDef.getFailureWorkflow()));

            Task task1try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.integration.worker", "failure...0");
            verifyPolledAndAcknowledgedTask(task1try1);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
            assertEquals(
                    workflow.getTasks().get(1).getTaskId(),
                    workflow.getTasks().get(1).getInputData().get("p3"));

            Task task1try2 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.integration.worker", "failure...0");
            verifyPolledAndAcknowledgedTask(task1try2);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());

            workflowExecutor.retry(workflowInstanceId, false);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(3, workflow.getTasks().size());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertEquals(
                    workflow.getTasks().get(2).getTaskId(),
                    workflow.getTasks().get(2).getInputData().get("p3"));

            Task task1try3 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task2.integration.worker");
            verifyPolledAndAcknowledgedTask(task1try3);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(4, workflow.getTasks().size());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());

            Task task2try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task2.integration.worker");
            verifyPolledAndAcknowledgedTask(task2try1);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(4, workflow.getTasks().size());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
            assertEquals(new HashSet<>(List.of("t1")), workflow.getFailedReferenceTaskNames());
            assertEquals(
                    new HashSet<>(List.of("integration_task_1")), workflow.getFailedTaskNames());
        } finally {
            metadataService.updateTaskDef(integrationTask1Definition);
        }
    }

    @Test
    @DisplayName("Test Long running simple workflow")
    void testLongRunningSimpleWorkflow() throws InterruptedException {
        String correlationId = "integration_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

        assertNotNull(workflowInstanceId);
        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        Task pollTaskTry1 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        pollTaskTry1.getOutputData().put("op", "task1.in.progress");
        pollTaskTry1.setCallbackAfterSeconds(5);
        pollTaskTry1.setStatus(Task.Status.IN_PROGRESS);
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1));

        assertNotNull(pollTaskTry1);
        assertThat(pollTaskTry1.getInputData()).containsKey("p1");
        assertEquals("p1 value", pollTaskTry1.getInputData().get("p1"));
        assertThat(pollTaskTry1.getInputData()).containsKey("p2");
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        Task pollTaskTry2 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        assertNull(pollTaskTry2);

        Thread.sleep(5000);
        Task task1try3 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(task1try3, Map.of());

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals("task1.done", workflow.getTasks().get(0).getOutputData().get("op"));

        Task task2try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");
        verifyPolledAndAcknowledgedTask(task2try1, Map.of("tp2", "task1.done", "tp1", "p1 value"));

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
    }

    @Test
    @DisplayName("Test simple workflow when the task's call back after seconds are reset")
    void testSimpleWorkflowCallbackSecondsReset() throws InterruptedException {
        String correlationId = "integration_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

        assertNotNull(workflowInstanceId);
        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        Task pollTaskTry1 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        pollTaskTry1.getOutputData().put("op", "task1.in.progress");
        pollTaskTry1.setCallbackAfterSeconds(3600);
        pollTaskTry1.setStatus(Task.Status.IN_PROGRESS);
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1));

        assertNotNull(pollTaskTry1);
        assertThat(pollTaskTry1.getInputData()).containsKey("p1");
        assertEquals("p1 value", pollTaskTry1.getInputData().get("p1"));
        assertThat(pollTaskTry1.getInputData()).containsKey("p2");
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        Task pollTaskTry2 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        assertNull(pollTaskTry2);

        Task pollTaskTry3 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        assertNull(pollTaskTry3);

        workflowExecutor.resetCallbacksForWorkflow(workflowInstanceId);

        Task task1try4 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(task1try4);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals("task1.done", workflow.getTasks().get(0).getOutputData().get("op"));

        Task task2try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");
        verifyPolledAndAcknowledgedTask(task2try1, Map.of("tp2", "task1.done", "tp1", "p1 value"));

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
    }

    @Test
    @DisplayName("Test non restartable simple workflow")
    void testNonRestartableSimpleWorkflow() {
        TaskDef integrationTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        integrationTask1Definition.getName(),
                        integrationTask1Definition.getDescription(),
                        integrationTask1Definition.getOwnerEmail(),
                        0,
                        integrationTask1Definition.getTimeoutSeconds(),
                        integrationTask1Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTaskDefinition);

        WorkflowDef simpleWorkflowDefinition =
                metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        simpleWorkflowDefinition.setName(INTEGRATION_TEST_WF_NON_RESTARTABLE);
        simpleWorkflowDefinition.setRestartable(false);
        metadataService.updateWorkflowDef(simpleWorkflowDefinition);

        try {
            String correlationId = "integration_test_1";
            Map<String, Object> workflowInput = new HashMap<>();
            workflowInput.put("param1", "p1 value");
            workflowInput.put("param2", "p2 value");

            String workflowInstanceId =
                    startWorkflow(
                            INTEGRATION_TEST_WF_NON_RESTARTABLE,
                            1,
                            correlationId,
                            workflowInput,
                            null);

            Task task1try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.integration.worker", "failure...0");
            verifyPolledAndAcknowledgedTask(task1try1);

            var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            workflowExecutor.restart(workflowInstanceId, false);

            Task task1try2 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1",
                            "task1.integration.worker",
                            Map.of("op", "task1.done"));
            verifyPolledAndAcknowledgedTask(task1try2);

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());

            Task task2try1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_2", "task2.integration.worker");
            verifyPolledAndAcknowledgedTask(
                    task2try1, Map.of("tp2", "task1.done", "tp1", "p1 value"));

            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals("task1.done", workflow.getOutput().get("o3"));

            assertThrows(
                    NotFoundException.class,
                    () -> workflowExecutor.restart(workflowInstanceId, false));
        } finally {
            metadataService.updateTaskDef(integrationTask1Definition);
            simpleWorkflowDefinition.setName(LINEAR_WORKFLOW_T1_T2);
            simpleWorkflowDefinition.setRestartable(true);
            metadataService.updateWorkflowDef(simpleWorkflowDefinition);
        }
    }

    @Test
    @DisplayName("Test simple workflow when update task result with callback after seconds")
    void testSimpleWorkflowUpdateTaskWithCallbackAfterSeconds() {
        String correlationId = "integration_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, workflowInput, null);

        assertNotNull(workflowInstanceId);
        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        // Poll and update IN_PROGRESS with no callback — task is re-queued immediately
        Task pollTaskTry1 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        pollTaskTry1.getOutputData().put("op", "task1.in.progress");
        pollTaskTry1.setStatus(Task.Status.IN_PROGRESS);
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1));

        assertNotNull(pollTaskTry1);
        assertThat(pollTaskTry1.getInputData()).containsKey("p1");
        assertEquals("p1 value", pollTaskTry1.getInputData().get("p1"));
        assertThat(pollTaskTry1.getInputData()).containsKey("p2");
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(0, workflow.getTasks().get(0).getCallbackAfterSeconds());

        // Poll again and update IN_PROGRESS with 3600s callback — task re-queued with delay
        Task pollTaskTry2 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        pollTaskTry2.getOutputData().put("op", "task1.in.progress");
        pollTaskTry2.setStatus(Task.Status.IN_PROGRESS);
        pollTaskTry2.setCallbackAfterSeconds(3600);
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry2));

        assertNotNull(pollTaskTry2);
        assertEquals(
                1,
                (int)
                        workflowExecutionService
                                .getTaskQueueSizes(List.of("integration_task_1"))
                                .get("integration_task_1"));

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                pollTaskTry2.getCallbackAfterSeconds(),
                workflow.getTasks().get(0).getCallbackAfterSeconds());

        // Task is not available for polling while delayed
        Task pollTaskTry3 =
                workflowExecutionService.poll("integration_task_1", "task1.integration.worker");
        assertNull(pollTaskTry3);
    }
}
