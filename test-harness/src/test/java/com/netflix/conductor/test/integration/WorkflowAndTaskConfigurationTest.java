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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowAndTaskConfigurationTest extends AbstractSpecification {

    @Autowired QueueDAO queueDAO;

    private static final String LINEAR_WORKFLOW_T1_T2 = "integration_test_wf";
    private static final String TEMPLATED_LINEAR_WORKFLOW = "integration_test_template_wf";
    private static final String WORKFLOW_WITH_OPTIONAL_TASK = "optional_task_wf";
    private static final String WORKFLOW_WITH_PERMISSIVE_TASK = "permissive_task_wf";
    private static final String WORKFLOW_WITH_PERMISSIVE_OPTIONAL_TASK =
            "permissive_optional_task_wf";
    private static final String TEST_WORKFLOW = "integration_test_wf3";
    private static final String WAIT_TIME_OUT_WORKFLOW = "test_wait_timeout";

    @BeforeEach
    void setup() {
        // Register LINEAR_WORKFLOW_T1_T2, TEST_WORKFLOW, RTOWF, WORKFLOW_WITH_OPTIONAL_TASK,
        // WORKFLOW_WITH_PERMISSIVE_TASK, WORKFLOW_WITH_PERMISSIVE_OPTIONAL_TASK
        workflowTestUtil.registerWorkflows(
                "simple_workflow_1_integration_test.json",
                "simple_workflow_1_input_template_integration_test.json",
                "simple_workflow_3_integration_test.json",
                "simple_workflow_with_optional_task_integration_test.json",
                "simple_workflow_with_permissive_task_integration_test.json",
                "simple_workflow_with_permissive_optional_task_integration_test.json",
                "simple_wait_task_workflow_integration_test.json");
    }

    @Test
    @DisplayName("Test simple workflow which has an optional task")
    void testSimpleWorkflowWithOptionalTask() throws Exception {
        // given: A input parameters for a workflow with an optional task
        String correlationId = "integration_test" + UUID.randomUUID();
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        // when: An optional task workflow is started
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_OPTIONAL_TASK, 1, correlationId, workflowInput, null);

        // then: verify that the workflow has started and the optional task is in a scheduled state
        assertNotNull(workflowInstanceId);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(0).getTaskType());

        // when: The first optional task is polled and failed
        Task polledAndFailedTaskTry1 =
                workflowTestUtil.pollAndFailTask(
                        "task_optional", "task1.integration.worker", "NETWORK ERROR");

        // then: Verify that the task_optional was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1);

        // when: A decide is executed on the workflow
        workflowExecutor.decide(workflowInstanceId);

        // then: verify that the workflow is still running and the first optional task has failed
        // and the retry has kicked in
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(1).getTaskType());

        // when: Poll the optional task again and do not complete it and run decide
        workflowExecutionService.poll("task_optional", "task1.integration.worker");
        Thread.sleep(5000);
        workflowExecutor.decide(workflowInstanceId);

        // then: Ensure that the workflow is updated
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, workflow.getTasks().get(1).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());

        // when: The second task 'integration_task_2' is polled and completed
        sweep(workflowInstanceId);
        final Task[] task2Try1Holder = {null};
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            task2Try1Holder[0] =
                                    workflowTestUtil.pollAndCompleteTask(
                                            "integration_task_2", "task2.integration.worker");
                            return task2Try1Holder[0] != null;
                        });
        Task task2Try1 = task2Try1Holder[0];

        // then: Verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(task2Try1);

        // and: Ensure that the workflow is in completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
    }

    @Test
    @DisplayName("Test simple workflow which has a permissive task")
    void testSimpleWorkflowWithPermissiveTask() throws Exception {
        // given: A input parameters for a workflow with a permissive task
        String correlationId = "integration_test" + UUID.randomUUID();
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        // when: A permissive task workflow is started
        String workflowInstanceId =
                startWorkflow(WORKFLOW_WITH_PERMISSIVE_TASK, 1, correlationId, workflowInput, null);

        // then: verify that the workflow has started and the permissive task is in a scheduled
        // state
        assertNotNull(workflowInstanceId);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_permissive", workflow.getTasks().get(0).getTaskType());

        // when: The first permissive task is polled and failed
        Task polledAndFailedTaskTry1 =
                workflowTestUtil.pollAndFailTask(
                        "task_permissive", "task1.integration.worker", "NETWORK ERROR");

        // then: Verify that the task_permissive was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1);

        // when: A decide is executed on the workflow
        workflowExecutor.decide(workflowInstanceId);

        // then: verify that the workflow is still running and the first permissive task has failed
        // and the retry has kicked in
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_permissive", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("task_permissive", workflow.getTasks().get(1).getTaskType());

        // when: The first permissive task is polled and failed (try 2)
        Task polledAndFailedTaskTry2 =
                workflowTestUtil.pollAndFailTask(
                        "task_permissive", "task1.integration.worker", "NETWORK ERROR");

        // then: Verify that the task_permissive was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry2);

        workflowExecutor.decide(workflowInstanceId);

        // then: Ensure that the workflow is updated
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
        assertEquals("task_permissive", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());

        // when: The second task 'integration_task_2' is polled and completed
        sweep(workflowInstanceId);
        final Task[] task2Try1Holder = {null};
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            task2Try1Holder[0] =
                                    workflowTestUtil.pollAndCompleteTask(
                                            "integration_task_2", "task2.integration.worker");
                            return task2Try1Holder[0] != null;
                        });
        Task task2Try1 = task2Try1Holder[0];

        // then: Verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(task2Try1);

        // and: Ensure that the workflow is in FAILED state (permissive task exceeded retries)
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        String failedTaskId = workflow.getTasks().get(1).getTaskId();
        assertEquals(
                "Task " + failedTaskId + " failed with status: FAILED and reason: 'NETWORK ERROR'",
                workflow.getReasonForIncompletion());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
    }

    @Test
    @DisplayName("Test simple workflow which has a permissive optional task")
    void testSimpleWorkflowWithPermissiveOptionalTask() throws Exception {
        // given: A input parameters for a workflow with a permissive optional task
        String correlationId = "integration_test" + UUID.randomUUID();
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        // when: A permissive optional task workflow is started
        String workflowInstanceId =
                startWorkflow(
                        WORKFLOW_WITH_PERMISSIVE_OPTIONAL_TASK,
                        1,
                        correlationId,
                        workflowInput,
                        null);

        // then: verify that the workflow has started and the permissive optional task is scheduled
        assertNotNull(workflowInstanceId);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(0).getTaskType());

        // when: The first permissive optional task is polled and failed
        Task polledAndFailedTaskTry1 =
                workflowTestUtil.pollAndFailTask(
                        "task_optional", "task1.integration.worker", "NETWORK ERROR");

        // then: Verify that the task_optional was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1);

        // when: A decide is executed on the workflow
        workflowExecutor.decide(workflowInstanceId);

        // then: verify that the workflow is still running and the first permissive optional task
        // has failed and the retry has kicked in
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(1).getTaskType());

        // when: Poll the permissive optional task again and do not complete it and run decide
        workflowExecutionService.poll("task_optional", "task1.integration.worker");
        Thread.sleep(5000);
        workflowExecutor.decide(workflowInstanceId);

        // then: Ensure that the workflow is updated
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, workflow.getTasks().get(1).getStatus());
        assertEquals("task_optional", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());

        // when: The second task 'integration_task_2' is polled and completed
        Task task2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");

        // then: Verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(task2Try1);

        // and: Ensure that the workflow is in completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
    }

    @Test
    @DisplayName("test workflow with input template parsing")
    void testWorkflowWithInputTemplateParsing() {
        // given: Input parameters for a workflow with input template
        String correlationId = "integration_test" + UUID.randomUUID();
        Map<String, Object> workflowInput = new HashMap<>();
        // leave other params blank on purpose to test input templates
        workflowInput.put("param3", "external string");

        // when: Is executed and completes
        String workflowInstanceId =
                startWorkflow(TEMPLATED_LINEAR_WORKFLOW, 1, correlationId, workflowInput, null);
        workflowExecutor.decide(workflowInstanceId);
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: Verify that input template is processed
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        Map<String, Object> output = workflow.getOutput();
        assertEquals("task1.done", output.get("output"));
        assertEquals("external string", output.get("param3"));
        assertEquals(List.of("list", "of", "strings"), output.get("param2"));
        @SuppressWarnings("unchecked")
        Map<String, Object> param1 = (Map<String, Object>) output.get("param1");
        assertNotNull(param1);
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedObject = (Map<String, Object>) param1.get("nested_object");
        assertNotNull(nestedObject);
        assertEquals("nested_value", nestedObject.get("nested_key"));
    }

    @Test
    @DisplayName("Test simple workflow with task time out configuration")
    void testSimpleWorkflowWithTaskTimeOutConfiguration() throws Exception {
        // setup: Register a task definition with retry policy on time out
        TaskDef persistedTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedTaskDefinition =
                new TaskDef(
                        persistedTask1Definition.getName(),
                        persistedTask1Definition.getDescription(),
                        persistedTask1Definition.getOwnerEmail(),
                        1,
                        1,
                        1);
        modifiedTaskDefinition.setRetryDelaySeconds(0);
        modifiedTaskDefinition.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        metadataService.updateTaskDef(modifiedTaskDefinition);

        try {
            // when: A simple workflow is started that has a task with time out and retry configured
            String correlationId = "unit_test_1" + UUID.randomUUID();
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");
            input.put("failureWfName", "FanInOutTest");

            String workflowInstanceId =
                    startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

            // then: Ensure that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // and: The decider queue has one task that is ready to be polled
            assertEquals(1, queueDAO.getSize(Utils.DECIDER_QUEUE));

            // when: The first task 'integration_task_1' is polled and acknowledged
            Task task1Try1 = workflowExecutionService.poll("integration_task_1", "task1.worker");

            // then: Ensure that a task was polled
            assertNotNull(task1Try1);
            assertEquals(workflowInstanceId, task1Try1.getWorkflowInstanceId());

            // and: Ensure that the decider size queue is 1 to enable the evaluation
            assertEquals(1, queueDAO.getSize(Utils.DECIDER_QUEUE));

            // when: There is a delay of 3 seconds introduced and the workflow is swept
            Thread.sleep(3000);
            sweep(workflowInstanceId);

            // then: Ensure that the first task has been TIMED OUT and the next task is SCHEDULED
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.TIMED_OUT, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

            // when: Poll for the task again and acknowledge
            Task task1Try2 = workflowExecutionService.poll("integration_task_1", "task1.worker");

            // then: Ensure that a task was polled
            assertNotNull(task1Try2);
            assertEquals(workflowInstanceId, task1Try2.getWorkflowInstanceId());

            // when: There is a delay of 3 seconds introduced and the workflow is swept
            Thread.sleep(3000);
            sweep(workflowInstanceId);

            // then: Ensure that the workflow has TIMED OUT (exhausted retries)
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.TIMED_OUT, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.TIMED_OUT, workflow.getTasks().get(1).getStatus());
        } finally {
            // cleanup: Ensure that the changes of the 'integration_task_1' are reverted
            metadataService.updateTaskDef(persistedTask1Definition);
        }
    }

    @Test
    @DisplayName("Test workflow timeout configurations")
    void testWorkflowTimeoutConfigurations() throws Exception {
        // setup: Get the workflow definition and change the workflow configuration
        WorkflowDef testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1);
        testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        testWorkflowDefinition.setTimeoutSeconds(5);
        metadataService.updateWorkflowDef(testWorkflowDefinition);

        try {
            // when: A simple workflow is started that has a workflow timeout configured
            String correlationId = "unit_test_3" + UUID.randomUUID();
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");
            input.put("failureWfName", "FanInOutTest");

            String workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1, correlationId, input, null);

            // then: Ensure that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: The first task 'integration_task_1' is polled and acknowledged
            Task task1Try1 = workflowExecutionService.poll("integration_task_1", "task1.worker");

            // then: Ensure that a task was polled
            assertNotNull(task1Try1);
            assertEquals(workflowInstanceId, task1Try1.getWorkflowInstanceId());

            // when: There is a delay of 6 seconds introduced and the workflow is swept
            Thread.sleep(6000);
            sweep(workflowInstanceId);

            // then: Ensure that the workflow has timed out
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(0).getStatus());
        } finally {
            // cleanup: Ensure that the workflow configuration changes are reverted
            testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
            testWorkflowDefinition.setTimeoutSeconds(0);
            metadataService.updateWorkflowDef(testWorkflowDefinition);
        }
    }

    @Test
    @DisplayName("Test retrying a timed out workflow due to workflow timeout")
    void testRetryingTimedOutWorkflowDueToWorkflowTimeout() throws Exception {
        // setup: Get the workflow definition and change the workflow configuration
        WorkflowDef testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1);
        testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        testWorkflowDefinition.setTimeoutSeconds(5);
        metadataService.updateWorkflowDef(testWorkflowDefinition);

        try {
            // when: A simple workflow is started that has a workflow timeout configured
            String correlationId = "retry_timeout_wf";
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");

            String workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1, correlationId, input, null);

            // then: Ensure that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: The first task 'integration_task_1' is polled and acknowledged
            Task task1Try1 = workflowExecutionService.poll("integration_task_1", "task1.worker");

            // then: Ensure that a task was polled
            assertNotNull(task1Try1);
            assertEquals(workflowInstanceId, task1Try1.getWorkflowInstanceId());

            // when: There is a delay of 6 seconds introduced and the workflow is swept
            Thread.sleep(6000);
            sweep(workflowInstanceId);

            // then: Ensure that the workflow has timed out
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
            assertEquals(0, workflow.getLastRetriedTime());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(0).getStatus());

            // when: Retrying the workflow
            workflowExecutor.retry(workflowInstanceId, false);

            // then: Ensure that the workflow is RUNNING and task is retried
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertNotEquals(0, workflow.getLastRetriedTime());
            assertEquals(2, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.CANCELED, workflow.getTasks().get(0).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        } finally {
            // cleanup: Ensure that the workflow configuration changes are reverted
            testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
            testWorkflowDefinition.setTimeoutSeconds(0);
            metadataService.updateWorkflowDef(testWorkflowDefinition);
        }
    }

    @Test
    @DisplayName(
            "Test retrying a timed out workflow due to workflow timeout without unsuccessful tasks")
    void testRetryingTimedOutWorkflowWithoutUnsuccessfulTasks() throws Exception {
        // setup: Get the workflow definition and change the workflow configuration
        WorkflowDef testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1);
        testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        testWorkflowDefinition.setTimeoutSeconds(5);
        metadataService.updateWorkflowDef(testWorkflowDefinition);

        try {
            // when: A simple workflow is started that has a workflow timeout configured
            String correlationId = "retry_timeout_wf";
            Map<String, Object> input = new HashMap<>();
            String inputParam1 = "p1 value";
            input.put("param1", inputParam1);
            input.put("param2", "p2 value");

            String workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1, correlationId, input, null);

            // then: Ensure that the workflow has started
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

            // when: The first task 'integration_task_1' is polled and acknowledged
            Task task1 = workflowExecutionService.poll("integration_task_1", "task1.worker");

            // then: Ensure that a task was polled
            assertNotNull(task1);
            assertEquals(workflowInstanceId, task1.getWorkflowInstanceId());

            // when: The task is completed and then a delay triggers workflow timeout
            task1.setStatus(Task.Status.COMPLETED);
            workflowExecutor.updateTask(new TaskResult(task1));
            Thread.sleep(6000);
            sweep(workflowInstanceId);

            // then: verify that the workflow is TIMED_OUT and task1 is still COMPLETED
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());

            // when: Retrying the workflow
            workflowExecutor.retry(workflowInstanceId, false);
            sweep(workflowInstanceId);

            // then: Ensure that the workflow is RUNNING and the completed task is preserved
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertNotEquals(0, workflow.getLastRetriedTime());
            assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            boolean hasScheduledTask2 =
                    workflow.getTasks().stream()
                            .anyMatch(
                                    t ->
                                            "integration_task_2".equals(t.getTaskType())
                                                    && Task.Status.SCHEDULED == t.getStatus());
            assertTrue(hasScheduledTask2);
        } finally {
            // cleanup: Ensure that the workflow configuration changes are reverted
            testWorkflowDefinition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
            testWorkflowDefinition.setTimeoutSeconds(0);
            metadataService.updateWorkflowDef(testWorkflowDefinition);
        }
    }

    @Test
    @DisplayName("Test re-running the simple workflow multiple times after completion")
    void testReRunningSimpleWorkflowMultipleTimesAfterCompletion() {
        // given: input required to start the workflow execution
        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        // when: Start a workflow based on the registered simple workflow
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

        // then: verify that the workflow is in a running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: Poll and complete the 'integration_task_1'
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // and: verify that 'integration_task1' is complete and the next task is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: poll and complete 'integration_task_2'
        Task pollAndCompleteTask2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");

        // then: verify that the 'integration_task_2' has been polled and acknowledged
        verifyPolledAndAcknowledgedTask(
                pollAndCompleteTask2Try1, Map.of("tp1", inputParam1, "tp2", "task1.done"));

        // and: verify that the workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getOutput().containsKey("o3"));

        // when: The completed workflow is re run after integration_task_1
        RerunWorkflowRequest reRunWorkflowRequest1 = new RerunWorkflowRequest();
        reRunWorkflowRequest1.setReRunFromWorkflowId(workflowInstanceId);
        String reRunTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(1)
                        .getTaskId();
        reRunWorkflowRequest1.setReRunFromTaskId(reRunTaskId);
        String reRun1WorkflowInstanceId = workflowExecutor.rerun(reRunWorkflowRequest1);

        // then: Verify that the workflow is in running state and has started after task 1
        Workflow reRun1Workflow =
                workflowExecutionService.getExecutionStatus(reRun1WorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, reRun1Workflow.getStatus());
        assertEquals(2, reRun1Workflow.getTasks().size());
        assertEquals("integration_task_1", reRun1Workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, reRun1Workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", reRun1Workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, reRun1Workflow.getTasks().get(1).getStatus());

        // when: poll and complete 'integration_task_2' (rerun 1)
        Task pollAndCompleteReRunTask2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");

        // then: verify that the 'integration_task_2' has been polled and acknowledged
        verifyPolledAndAcknowledgedTask(
                pollAndCompleteReRunTask2Try1, Map.of("tp1", inputParam1, "tp2", "task1.done"));

        // and: verify that the re run workflow is in a completed state
        reRun1Workflow =
                workflowExecutionService.getExecutionStatus(reRun1WorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, reRun1Workflow.getStatus());
        assertEquals(2, reRun1Workflow.getTasks().size());
        assertEquals("integration_task_2", reRun1Workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, reRun1Workflow.getTasks().get(1).getStatus());
        assertTrue(reRun1Workflow.getOutput().containsKey("o3"));

        // when: The completed workflow is re run (from the beginning)
        RerunWorkflowRequest reRunWorkflowRequest2 = new RerunWorkflowRequest();
        reRunWorkflowRequest2.setReRunFromWorkflowId(workflowInstanceId);
        String reRun2WorkflowInstanceId = workflowExecutor.rerun(reRunWorkflowRequest2);

        // then: verify that the workflow is in a running state
        Workflow reRun2Workflow =
                workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, reRun2Workflow.getStatus());
        assertEquals(1, reRun2Workflow.getTasks().size());
        assertEquals("integration_task_1", reRun2Workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, reRun2Workflow.getTasks().get(0).getStatus());

        // when: Poll and complete the 'integration_task_1' (rerun 2)
        Task pollAndCompleteReRun2Task1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteReRun2Task1Try1);

        // and: verify that 'integration_task1' is complete and the next task is scheduled
        reRun2Workflow =
                workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, reRun2Workflow.getStatus());
        assertEquals(2, reRun2Workflow.getTasks().size());
        assertEquals("integration_task_1", reRun2Workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, reRun2Workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", reRun2Workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, reRun2Workflow.getTasks().get(1).getStatus());

        // when: poll and complete 'integration_task_2' (rerun 2)
        Task pollAndCompleteReRun2Task2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");

        // then: verify that the 'integration_task_2' has been polled and acknowledged
        verifyPolledAndAcknowledgedTask(
                pollAndCompleteReRun2Task2Try1, Map.of("tp1", inputParam1, "tp2", "task1.done"));

        // and: verify that the workflow is in a completed state
        reRun2Workflow =
                workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, reRun2Workflow.getStatus());
        assertEquals(2, reRun2Workflow.getTasks().size());
        assertEquals("integration_task_2", reRun2Workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, reRun2Workflow.getTasks().get(1).getStatus());
        assertTrue(reRun2Workflow.getOutput().containsKey("o3"));
    }

    @Test
    @DisplayName("Test task skipping in simple workflows")
    void testTaskSkippingInSimpleWorkflows() {
        // when: A simple workflow is started
        String correlationId = "unit_test_3" + UUID.randomUUID();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        String workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1, correlationId, input, null);

        // then: Ensure that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: The second task in the workflow is skipped
        workflowExecutor.skipTaskFromWorkflow(workflowInstanceId, "t2", null);

        // then: Ensure that the second task is skipped and the first one is still scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: Poll and complete the 'integration_task_1'
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // and: Ensure that the third task is scheduled and the first one is in complete state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_3", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

        // when: Poll and complete the 'integration_task_3'
        Task pollAndCompleteTask3Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "task3.integration.worker");

        // then: verify that the 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1);

        // and: verify that the workflow is in a complete state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_3", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
    }

    @Test
    @DisplayName("Test pause and resume simple workflow")
    void testPauseAndResumeSimpleWorkflow() {
        // given: input required to start the workflow execution
        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        // when: Start a workflow based on the registered simple workflow
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

        // then: verify that the workflow is in a running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: The running workflow is paused
        workflowExecutor.pauseWorkflow(workflowInstanceId);

        // and: Poll and complete the 'integration_task_1'
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // and: verify that the workflow is in PAUSED state and the next task is not scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.PAUSED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());

        // when: The next task in the workflow is polled for
        Task task2Try1 =
                workflowExecutionService.poll("integration_task_2", "task2.integration.worker");

        // then: verify that there was no task polled
        assertNull(task2Try1);

        // when: A decide is run explicitly
        workflowExecutor.decide(workflowInstanceId);

        // and: The next task is polled again
        Task task2Try2 =
                workflowExecutionService.poll("integration_task_2", "task2.integration.worker");

        // then: verify that there was no task polled
        assertNull(task2Try2);

        // when: The workflow is resumed
        workflowExecutor.resumeWorkflow(workflowInstanceId);

        // then: verify that the workflow was resumed and the next task is in a scheduled state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: poll and complete 'integration_task_2'
        Task pollAndCompleteTask2Try3 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker");

        // then: verify that the 'integration_task_2' has been polled and acknowledged
        verifyPolledAndAcknowledgedTask(
                pollAndCompleteTask2Try3, Map.of("tp1", inputParam1, "tp2", "task1.done"));

        // and: verify that the workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertTrue(workflow.getOutput().containsKey("o3"));
    }

    @Test
    @DisplayName("Test wait time out task based simple workflow")
    void testWaitTimeOutTaskBasedSimpleWorkflow() throws Exception {
        // when: Start a workflow based on a task that has a registered wait time out
        String workflowInstanceId =
                startWorkflow(WAIT_TIME_OUT_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: verify that the workflow is running and the first task scheduled
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("WAIT", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());

        // when: A delay is introduced
        Thread.sleep(3000);

        // and: A decide is executed on the workflow
        workflowExecutor.decide(workflowInstanceId);

        // then: verify that a replacement task has been scheduled due to time out
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("WAIT", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.TIMED_OUT, workflow.getTasks().get(0).getStatus());
        assertEquals("WAIT", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: The wait task is completed
        Task waitTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(1);
        waitTask.setStatus(Task.Status.COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        // and: verify that the workflow is in running state and the next task is scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("WAIT", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

        // and: Poll and complete the 'integration_task_1'
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // and: The workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
    }

    @Test
    @DisplayName("Test simple workflow with callbackAfterSeconds for tasks")
    void testSimpleWorkflowWithCallbackAfterSecondsForTasks() throws Exception {
        // given: input required to start the workflow execution
        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        // when: Start a workflow based on the registered simple workflow
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null);

        // then: Ensure that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: The first task is polled and then a callbackAfterSeconds is added to the task
        Task task1Try1 = workflowExecutionService.poll("integration_task_1", "task1.worker");
        task1Try1.setStatus(Task.Status.IN_PROGRESS);
        task1Try1.setCallbackAfterSeconds(2L);
        workflowExecutionService.updateTask(new TaskResult(task1Try1));

        // then: verify that the workflow is in running state and the task is in SCHEDULED
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: the 'integration_task_1' is polled again
        Task task1Try2 = workflowExecutionService.poll("integration_task_1", "task1.worker");

        // then: Ensure that there was no task polled due to the callBackAfterSeconds
        assertNull(task1Try2);

        // then: verify that the workflow is in running state and the task is in scheduled state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: There is a delay introduced to go over the callbackAfterSeconds interval
        Thread.sleep(2050);

        // and: the 'integration_task_1' is polled and completed
        Task pollAndCompleteTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1);

        // and: verify that the workflow has moved forward and 'integration_task_1' is completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: The second task is polled and then a callbackAfterSeconds is added to the task
        Task task2Try1 = workflowExecutionService.poll("integration_task_2", "task2.worker");
        task2Try1.setStatus(Task.Status.IN_PROGRESS);
        task2Try1.setCallbackAfterSeconds(5L);
        workflowExecutionService.updateTask(new TaskResult(task2Try1));

        // then: Verify that the workflow is in running state and the task is in scheduled state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: poll for 'integration_task_2'
        Task task2Try2 = workflowExecutionService.poll("integration_task_2", "task2.worker");

        // then: Ensure that there was no task polled due to the callBackAfterSeconds
        assertNull(task2Try2);

        // when: A delay is introduced to get over the callBackAfterSeconds interval
        Thread.sleep(5100);

        // and: the 'integration_task_2' is polled and completed
        Task pollAndCompleteTask2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task1.integration.worker");

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1);

        // and: verify that the workflow has completed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
    }

    @Test
    @DisplayName("Test workflow with no tasks")
    void testWorkflowWithNoTasks() {
        // setup: Create a workflow definition with no tasks
        WorkflowDef emptyWorkflowDef = new WorkflowDef();
        emptyWorkflowDef.setName("empty_workflow");
        emptyWorkflowDef.setSchemaVersion(2);

        // when: a workflow is started with this definition
        Map<String, Object> input = new HashMap<>();
        String correlationId = "empty_workflow";
        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setWorkflowDefinition(emptyWorkflowDef);
        startWorkflowInput.setWorkflowInput(input);
        startWorkflowInput.setCorrelationId(correlationId);
        String workflowInstanceId = workflowExecutor.startWorkflow(startWorkflowInput);

        // then: the workflow is completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(0, workflow.getTasks().size());
    }

    @Test
    @DisplayName("Test task def template")
    void testTaskDefTemplate() {
        // setup: Register a task definition with input template
        TaskDef templatedTask = new TaskDef();
        templatedTask.setName("templated_task");
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("method", "GET");
        httpRequest.put("vipStack", "${STACK2}");
        httpRequest.put("uri", "/get/something");
        Map<String, Object> body = new HashMap<>();
        body.put("inputPaths", Arrays.asList("${workflow.input.path1}", "${workflow.input.path2}"));
        body.put("requestDetails", "${workflow.input.requestDetails}");
        body.put("outputPath", "${workflow.input.outputPath}");
        httpRequest.put("body", body);
        templatedTask.getInputTemplate().put("http_request", httpRequest);
        templatedTask.setOwnerEmail("test@harness.com");
        metadataService.registerTaskDef(Arrays.asList(templatedTask));

        // and: set a system property for STACK2
        System.setProperty("STACK2", "test_stack");

        // and: a workflow definition using this task is created
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(templatedTask.getName());
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t0");

        WorkflowDef templateWorkflowDef = new WorkflowDef();
        templateWorkflowDef.setName("template_workflow");
        templateWorkflowDef.getTasks().add(workflowTask);
        templateWorkflowDef.setSchemaVersion(2);
        templateWorkflowDef.setOwnerEmail("test@harness.com");
        metadataService.registerWorkflowDef(templateWorkflowDef);

        // and: the input to the workflow is curated
        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("key1", "value1");
        requestDetails.put("key2", 42);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("path1", "file://path1");
        workflowInput.put("path2", "file://path2");
        workflowInput.put("outputPath", "s3://bucket/outputPath");
        workflowInput.put("requestDetails", requestDetails);

        // when: the workflow is started
        String correlationId = "workflow_taskdef_template";
        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setWorkflowDefinition(templateWorkflowDef);
        startWorkflowInput.setWorkflowInput(workflowInput);
        startWorkflowInput.setCorrelationId(correlationId);
        String workflowInstanceId = workflowExecutor.startWorkflow(startWorkflowInput);

        // then: the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        @SuppressWarnings("unchecked")
        Map<String, Object> taskHttpRequest =
                (Map<String, Object>) workflow.getTasks().get(0).getInputData().get("http_request");
        assertNotNull(taskHttpRequest);
        assertEquals("GET", taskHttpRequest.get("method"));
        assertEquals("test_stack", taskHttpRequest.get("vipStack"));
        assertTrue(taskHttpRequest.get("body") instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> taskBody = (Map<String, Object>) taskHttpRequest.get("body");
        assertTrue(taskBody.get("requestDetails") instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> taskRequestDetails =
                (Map<String, Object>) taskBody.get("requestDetails");
        assertEquals("value1", taskRequestDetails.get("key1"));
        assertEquals(42, taskRequestDetails.get("key2"));
        assertEquals("s3://bucket/outputPath", taskBody.get("outputPath"));
        assertTrue(taskBody.get("inputPaths") instanceof List);

        @SuppressWarnings("unchecked")
        List<Object> inputPaths = (List<Object>) taskBody.get("inputPaths");
        assertEquals("file://path1", inputPaths.get(0));
        assertEquals("file://path2", inputPaths.get(1));
        assertEquals("/get/something", taskHttpRequest.get("uri"));
    }

    @Test
    @DisplayName("Test task def created if not exist")
    void testTaskDefCreatedIfNotExist() {
        // setup: Register a workflow definition with task def not registered
        String taskDefName = "task_not_registered";
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(taskDefName);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t0");

        WorkflowDef testWorkflowDef = new WorkflowDef();
        testWorkflowDef.setName("test_workflow");
        testWorkflowDef.getTasks().add(workflowTask);
        testWorkflowDef.setSchemaVersion(2);
        testWorkflowDef.setOwnerEmail("test@harness.com");
        metadataService.registerWorkflowDef(testWorkflowDef);

        // when: the workflow is started
        String correlationId = "workflow_taskdef_not_registered";
        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setWorkflowDefinition(testWorkflowDef);
        startWorkflowInput.setWorkflowInput(new HashMap<>());
        startWorkflowInput.setCorrelationId(correlationId);
        String workflowInstanceId = workflowExecutor.startWorkflow(startWorkflowInput);

        // then: the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(taskDefName, workflow.getTasks().get(0).getTaskDefName());
    }
}
