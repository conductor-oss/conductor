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
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class ForkJoinTest extends AbstractSpecification {

    @Autowired Join joinTask;

    @Autowired SubWorkflow subWorkflowTask;

    private static final String FORK_JOIN_WF = "FanInOutTest";
    private static final String FORK_JOIN_NESTED_WF = "FanInOutNestedTest";
    private static final String FORK_JOIN_NESTED_SUB_WF = "FanInOutNestedSubWorkflowTest";
    private static final String WORKFLOW_FORK_JOIN_OPTIONAL_SW =
            "integration_test_fork_join_optional_sw";
    private static final String FORK_JOIN_SUB_WORKFLOW = "integration_test_fork_join_sw";
    private static final String FORK_JOIN_PERMISSIVE_WF = "FanInOutPermissiveTest";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "fork_join_integration_test.json",
                "fork_join_with_no_task_retry_integration_test.json",
                "fork_join_with_no_permissive_task_retry_integration_test.json",
                "nested_fork_join_integration_test.json",
                "simple_workflow_1_integration_test.json",
                "nested_fork_join_with_sub_workflow_integration_test.json",
                "simple_one_task_sub_workflow_integration_test.json",
                "fork_join_with_optional_sub_workflow_forks_integration_test.json",
                "fork_join_sub_workflow.json",
                "fork_join_permissive_integration_test.json");
    }

    /** start | fork / \ task1 task2 | / task3 / \ / \ / join | task4 | End */
    @Test
    @DisplayName("Test a simple workflow with fork join success flow")
    void testSimpleWorkflowWithForkJoinSuccessFlow() {
        // when: A fork join workflow is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_WF, 1, "fanoutTest", new HashMap<>(), null);

        // then: verify that the workflow has started and the starting nodes of each fork are in
        // scheduled state
        assertNotNull(workflowInstanceId);
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

        // when: The first task of the fork is polled and completed
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        Task polledAndAckTask1Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_1", "task1.worker");

        // then: verify that 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);

        // and: The workflow has been updated and has all required tasks in the right status
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(5, wf2.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", wf2.getTasks().get(4).getTaskType());

        // when: The 'integration_task_3' is polled and completed
        Task polledAndAckTask3Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_3", "task1.worker");

        // then: verify that 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask3Try1);

        // and: The workflow has been updated with the task status and task list
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
        assertEquals(5, wf3.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, wf3.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", wf3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(4).getStatus());
        assertEquals("integration_task_3", wf3.getTasks().get(4).getTaskType());

        // when: The other node of the fork is completed by completing 'integration_task_2'
        Task polledAndAckTask2Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_2", "task1.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

        // when: JOIN task executed by the async executor
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: The workflow has been updated with the task status and task list
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf4.getStatus());
        assertEquals(6, wf4.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(2).getStatus());
        assertEquals("integration_task_2", wf4.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(5).getStatus());
        assertEquals("integration_task_4", wf4.getTasks().get(5).getTaskType());

        // when: The last task of the workflow is then polled and completed
        Task polledAndAckTask4Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_4", "task1.worker");

        // then: verify that 'integration_task_4' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask4Try1);

        // and: Then verify that the workflow is completed
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf5.getStatus());
        assertEquals(6, wf5.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(5).getStatus());
        assertEquals("integration_task_4", wf5.getTasks().get(5).getTaskType());
    }

    @Test
    @DisplayName("Test a simple workflow with fork join failure flow")
    void testSimpleWorkflowWithForkJoinFailureFlow() {
        // setup: Ensure that 'integration_task_2' has a retry count of 0
        TaskDef persistedIntegrationTask2Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_2").get();
        TaskDef modifiedIntegrationTask2Definition =
                new TaskDef(
                        persistedIntegrationTask2Definition.getName(),
                        persistedIntegrationTask2Definition.getDescription(),
                        persistedIntegrationTask2Definition.getOwnerEmail(),
                        0,
                        0,
                        persistedIntegrationTask2Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedIntegrationTask2Definition);

        try {
            // when: A fork join workflow is started
            String workflowInstanceId =
                    startWorkflow(FORK_JOIN_WF, 1, "fanoutTest", new HashMap<>(), null);

            // then: verify that the workflow has started and the starting nodes are in scheduled
            // state
            assertNotNull(workflowInstanceId);
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
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

            // when: The first task of the fork is polled and completed
            Task polledAndAckTask1Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_1", "task1.worker");

            // then: verify that 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);

            // and: The workflow has been updated and has all required tasks in the right status
            Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
            assertEquals(5, wf2.getTasks().size());
            assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", wf2.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", wf2.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());
            assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(4).getStatus());
            assertEquals("integration_task_3", wf2.getTasks().get(4).getTaskType());

            // when: The other node of the fork is failed by failing 'integration_task_2'
            Task polledAndAckTask2Try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_2", "task1.worker", "Failed....");

            // and: workflow is evaluated
            sweep(workflowInstanceId);

            // then: verify that 'integration_task_2' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

            // and: the workflow is in the failed state
            Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, wf3.getStatus());
            assertEquals(5, wf3.getTasks().size());
            assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", wf3.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.FAILED, wf3.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", wf3.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.CANCELED, wf3.getTasks().get(3).getStatus());
            assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
            assertEquals(Task.Status.CANCELED, wf3.getTasks().get(4).getStatus());
            assertEquals("integration_task_3", wf3.getTasks().get(4).getTaskType());
        } finally {
            // cleanup: Restore the task definitions that were modified as part of this feature
            // testing
            metadataService.updateTaskDef(persistedIntegrationTask2Definition);
        }
    }

    /** start | fork / \ p_task1 p_task2 | / \ / \ / join | s_task3 | End */
    @Test
    @DisplayName("Test a simple workflow with fork join permissive failure flow")
    void testSimpleWorkflowWithForkJoinPermissiveFailureFlow() {
        // setup: Ensure that 'integration_task_1' has a retry count of 0
        TaskDef persistedIntegrationTask1Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get();
        TaskDef modifiedIntegrationTask1Definition =
                new TaskDef(
                        persistedIntegrationTask1Definition.getName(),
                        persistedIntegrationTask1Definition.getDescription(),
                        persistedIntegrationTask1Definition.getOwnerEmail(),
                        0,
                        0,
                        persistedIntegrationTask1Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedIntegrationTask1Definition);

        try {
            // when: A fork join workflow is started
            String workflowInstanceId =
                    startWorkflow(FORK_JOIN_PERMISSIVE_WF, 1, "fanoutTest", new HashMap<>(), null);

            // then: verify that the workflow has started and the starting nodes are in scheduled
            // state
            assertNotNull(workflowInstanceId);
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(4, workflow.getTasks().size());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
            assertTrue(workflow.getTasks().get(1).getWorkflowTask().isPermissive());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertTrue(workflow.getTasks().get(2).getWorkflowTask().isPermissive());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
            assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());

            // when: The first task of the fork is polled and failed
            String joinTaskId =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("fanouttask_join")
                            .getTaskId();
            Task polledAndAckTask1Try1 =
                    workflowTestUtil.pollAndFailTask(
                            "integration_task_1", "task1.worker", "Failed...");

            // then: verify that 'integration_task_1' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);

            // and: The workflow has been updated and has all required tasks in the right status
            Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
            assertEquals(4, wf2.getTasks().size());
            assertEquals(Task.Status.FAILED, wf2.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", wf2.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", wf2.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());
            assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());

            // when: The other node of the fork is completed by completing 'integration_task_2'
            Task polledAndAckTask2Try1 =
                    workflowTestUtil.pollAndCompleteTask("integration_task_2", "task1.worker");

            // and: workflow is evaluated
            sweep(workflowInstanceId);

            // then: verify that 'integration_task_2' was polled and acknowledged
            verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

            // and: the workflow is in the running state (permissive tasks haven't all finished)
            Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
            assertEquals(4, wf3.getTasks().size());
            assertEquals(Task.Status.FAILED, wf3.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", wf3.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", wf3.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(3).getStatus());
            assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());

            // when: JOIN task executed by the async executor
            asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

            // then: The workflow has been updated with the task status and task list
            Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, wf4.getStatus());
            assertEquals(4, wf4.getTasks().size());
            assertEquals(Task.Status.FAILED, wf4.getTasks().get(1).getStatus());
            assertEquals("integration_task_1", wf4.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(2).getStatus());
            assertEquals("integration_task_2", wf4.getTasks().get(2).getTaskType());
            assertEquals(Task.Status.FAILED, wf4.getTasks().get(3).getStatus());
            assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        } finally {
            // cleanup: Restore the task definitions that were modified as part of this feature
            // testing
            metadataService.updateTaskDef(persistedIntegrationTask1Definition);
        }
    }

    @Test
    @DisplayName("Test retrying a failed fork join workflow")
    void testRetryingAFailedForkJoinWorkflow() {
        // when: A fork join workflow is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_WF + "_2", 1, "fanoutTest", new HashMap<>(), null);

        // then: verify that the workflow has started and the starting nodes are in scheduled state
        assertNotNull(workflowInstanceId);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_0_RT_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_0_RT_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());

        // when: The first task of the fork is polled and completed
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        Task polledAndAckTask1Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_0_RT_1", "task1.worker");

        // then: verify that 'integration_task_0_RT_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);

        // and: The workflow has been updated and has all required tasks in the right status
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(5, wf2.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
        assertEquals("integration_task_0_RT_1", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
        assertEquals("integration_task_0_RT_2", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(4).getStatus());
        assertEquals("integration_task_0_RT_3", wf2.getTasks().get(4).getTaskType());

        // when: The other node of the fork is failed by failing 'integration_task_0_RT_2'
        Task polledAndAckTask2Try1 =
                workflowTestUtil.pollAndFailTask(
                        "integration_task_0_RT_2", "task1.worker", "Failed....");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_task_0_RT_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

        // and: the workflow is in the failed state
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, wf3.getStatus());
        assertEquals(5, wf3.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(1).getStatus());
        assertEquals("integration_task_0_RT_1", wf3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf3.getTasks().get(2).getStatus());
        assertEquals("integration_task_0_RT_2", wf3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.CANCELED, wf3.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.CANCELED, wf3.getTasks().get(4).getStatus());
        assertEquals("integration_task_0_RT_3", wf3.getTasks().get(4).getTaskType());

        // when: The workflow is retried
        workflowExecutor.retry(workflowInstanceId, false);

        // then: verify that the workflow is retried and new tasks are added in place of failed
        // tasks
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf4.getStatus());
        assertEquals(7, wf4.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(1).getStatus());
        assertEquals("integration_task_0_RT_1", wf4.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf4.getTasks().get(2).getStatus());
        assertEquals("integration_task_0_RT_2", wf4.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.CANCELED, wf4.getTasks().get(4).getStatus());
        assertEquals("integration_task_0_RT_3", wf4.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(5).getStatus());
        assertEquals("integration_task_0_RT_2", wf4.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(6).getStatus());
        assertEquals("integration_task_0_RT_3", wf4.getTasks().get(6).getTaskType());

        // when: The 'integration_task_0_RT_3' is polled and completed
        Task polledAndAckTask3Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_0_RT_3", "task1.worker");

        // then: verify that 'integration_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask3Try1);

        // when: The other node of the fork is completed by completing 'integration_task_0_RT_2'
        Task polledAndAckTask2Try2 =
                workflowTestUtil.pollAndCompleteTask("integration_task_0_RT_2", "task1.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try2);

        // when: JOIN task is polled and executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // and: The last task of the workflow is then polled and completed
        Task polledAndAckTask4Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_0_RT_4", "task1.worker");

        // then: verify that 'integration_task_0_RT_4' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask4Try1);

        // then: verify that the workflow is completed and the task list of execution is as expected
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf5.getStatus());
        assertEquals(8, wf5.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(1).getStatus());
        assertEquals("integration_task_0_RT_1", wf5.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf5.getTasks().get(2).getStatus());
        assertEquals("integration_task_0_RT_2", wf5.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf5.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.CANCELED, wf5.getTasks().get(4).getStatus());
        assertEquals("integration_task_0_RT_3", wf5.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(5).getStatus());
        assertEquals("integration_task_0_RT_2", wf5.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(6).getStatus());
        assertEquals("integration_task_0_RT_3", wf5.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(7).getStatus());
        assertEquals("integration_task_0_RT_4", wf5.getTasks().get(7).getTaskType());
    }

    @Test
    @DisplayName("Test retrying a failed permissive fork join workflow")
    void testRetryingAFailedPermissiveForkJoinWorkflow() {
        // when: A fork join permissive workflow is started
        String workflowInstanceId =
                startWorkflow(
                        FORK_JOIN_PERMISSIVE_WF + "_2", 1, "fanoutTest", new HashMap<>(), null);

        // then: verify that the workflow has started and the starting nodes are in scheduled state
        assertNotNull(workflowInstanceId);
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());

        // when: The first task of the fork is polled and completed
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        Task polledAndAckTask1Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_p_task_0_RT_1", "task1.worker");

        // then: verify that 'integration_task_p_0_RT_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);

        // and: The workflow has been updated and has all required tasks in the right status
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(5, wf2.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(4).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf2.getTasks().get(4).getTaskType());

        // when: The other node of the fork is failed by failing 'integration_p_task_0_RT_2'
        Task polledAndAckTask2Try1 =
                workflowTestUtil.pollAndFailTask(
                        "integration_p_task_0_RT_2", "task1.worker", "Failed....");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_p_task_0_RT_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

        // and: the workflow is not in the failed state, until the completion of the permissive
        // forked tasks
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
        assertEquals(5, wf3.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", wf3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf3.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf3.getTasks().get(4).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf3.getTasks().get(4).getTaskType());

        // when: The other node of the fork is failed by failing 'integration_p_task_0_RT_3'
        Task polledAndAckTask3Try1 =
                workflowTestUtil.pollAndFailTask(
                        "integration_p_task_0_RT_3", "task1.worker", "Failed....");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_p_task_0_RT_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask3Try1);

        // and: JOIN task is polled and executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // and: the workflow is in the failed state
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, wf4.getStatus());
        assertEquals(5, wf4.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", wf4.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf4.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf4.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.FAILED, wf4.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.FAILED, wf4.getTasks().get(4).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf4.getTasks().get(4).getTaskType());

        // when: The workflow is retried
        workflowExecutor.retry(workflowInstanceId, false);

        // then: verify that the workflow is retried and new tasks are added in place of failed
        // tasks
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf5.getStatus());
        assertEquals(7, wf5.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", wf5.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf5.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf5.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf5.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf5.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.FAILED, wf5.getTasks().get(4).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf5.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf5.getTasks().get(5).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf5.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf5.getTasks().get(6).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf5.getTasks().get(6).getTaskType());

        // when: The 'integration_p_task_0_RT_3' is polled and completed
        Task polledAndAckTask3Try2 =
                workflowTestUtil.pollAndCompleteTask("integration_p_task_0_RT_3", "task1.worker");

        // then: verify that 'integration_p_task_3' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask3Try2);

        // when: The other node of the fork is completed by completing 'integration_p_task_0_RT_2'
        Task polledAndAckTask2Try2 =
                workflowTestUtil.pollAndCompleteTask("integration_p_task_0_RT_2", "task1.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that 'integration_p_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try2);

        // when: JOIN task is polled and executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // and: The last task of the workflow is then polled and completed
        Task polledAndAckTask4Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_p_task_0_RT_4", "task1.worker");

        // then: verify that 'integration_p_task_0_RT_4' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask4Try1);

        // then: verify that the workflow is completed and the task list is as expected
        Workflow wf6 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf6.getStatus());
        assertEquals(8, wf6.getTasks().size());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(1).getStatus());
        assertEquals("integration_p_task_0_RT_1", wf6.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf6.getTasks().get(2).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf6.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(3).getStatus());
        assertEquals("JOIN", wf6.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.FAILED, wf6.getTasks().get(4).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf6.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(5).getStatus());
        assertEquals("integration_p_task_0_RT_2", wf6.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(6).getStatus());
        assertEquals("integration_p_task_0_RT_3", wf6.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(7).getStatus());
        assertEquals("integration_p_task_0_RT_4", wf6.getTasks().get(7).getTaskType());
    }

    @Test
    @DisplayName("Test nested fork join workflow success flow")
    void testNestedForkJoinWorkflowSuccessFlow() {
        // given: Input for the nested fork join workflow
        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");

        // when: A nested workflow is started with the input
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input, null);

        // then: verify that the workflow has started
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals(
                5,
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        List.of("t11", "t12", "t13", "fork1", "fork2")
                                                .contains(t.getReferenceTaskName()))
                        .count());
        assertEquals(
                0,
                workflow.getTasks().stream()
                        .filter(t -> List.of("t1", "t2", "t16").contains(t.getReferenceTaskName()))
                        .count());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_11", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_12", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_13", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());
        assertEquals(
                List.of("t14", "t20"), workflow.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());
        assertEquals(
                List.of("t11", "join2"), workflow.getTasks().get(6).getInputData().get("joinOn"));

        // when: Poll and Complete tasks: 'integration_task_11', 'integration_task_12' and
        // 'integration_task_13'
        String outerJoinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join1")
                        .getTaskId();
        String innerJoinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join2")
                        .getTaskId();
        Task polledAndAckTask11Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_11", "task11.worker");
        Task polledAndAckTask12Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_12", "task12.worker");
        Task polledAndAckTask13Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_13", "task13.worker");

        // then: verify that tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask11Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask12Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask13Try1);

        // and: verify the state of the workflow
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(10, wf2.getTasks().size());
        assertEquals("FORK", wf2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(0).getStatus());
        assertEquals("integration_task_11", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
        assertEquals("FORK", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(2).getStatus());
        assertEquals("integration_task_12", wf2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(3).getStatus());
        assertEquals("integration_task_13", wf2.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(4).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf2.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf2.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(6).getStatus());
        assertEquals(List.of("t11", "join2"), wf2.getTasks().get(6).getInputData().get("joinOn"));
        assertEquals("integration_task_14", wf2.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(7).getStatus());
        assertEquals("DECISION", wf2.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(8).getStatus());
        assertEquals("integration_task_16", wf2.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(9).getStatus());

        // when: Poll and Complete tasks: 'integration_task_16' and 'integration_task_14'
        Task polledAndAckTask16Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_16", "task16.worker");
        Task polledAndAckTask14Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_14", "task14.worker");

        // then: verify that tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask16Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask14Try1);

        // and: verify the state of the workflow
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
        assertEquals(11, wf3.getTasks().size());
        assertEquals("JOIN", wf3.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf3.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf3.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(6).getStatus());
        assertEquals(List.of("t11", "join2"), wf3.getTasks().get(6).getInputData().get("joinOn"));
        assertEquals("integration_task_14", wf3.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(7).getStatus());
        assertEquals("DECISION", wf3.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(8).getStatus());
        assertEquals("integration_task_16", wf3.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(9).getStatus());
        assertEquals("integration_task_19", wf3.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf3.getTasks().get(10).getStatus());

        // when: Poll and Complete tasks: 'integration_task_19'
        Task polledAndAckTask19Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_19", "task19.worker");

        // then: verify that task 'integration_task_19' polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask19Try1);

        // and: verify the state of the workflow
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf4.getStatus());
        assertEquals(12, wf4.getTasks().size());
        assertEquals("JOIN", wf4.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf4.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf4.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(6).getStatus());
        assertEquals(List.of("t11", "join2"), wf4.getTasks().get(6).getInputData().get("joinOn"));
        assertEquals("integration_task_19", wf4.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(10).getStatus());
        assertEquals("integration_task_20", wf4.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(11).getStatus());

        // when: Poll and Complete tasks: 'integration_task_20'
        Task polledAndAckTask20Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_20", "task20.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that task 'integration_task_20' is polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask20Try1);

        // when: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // then: verify the state of the workflow
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf5.getStatus());
        assertEquals(13, wf5.getTasks().size());
        assertEquals("JOIN", wf5.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf5.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf5.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(6).getStatus());
        assertEquals(List.of("t11", "join2"), wf5.getTasks().get(6).getInputData().get("joinOn"));
        assertEquals("integration_task_20", wf5.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(11).getStatus());
        assertEquals("integration_task_15", wf5.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf5.getTasks().get(12).getStatus());

        // when: Poll and Complete tasks: 'integration_task_15'
        Task polledAndAckTask15Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_15", "task15.worker");

        // then: verify that tasks 'integration_task_15' polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask15Try1);

        // and: verify that the workflow is in a complete state
        Workflow wf6 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf6.getStatus());
        assertEquals(13, wf6.getTasks().size());
        assertEquals("integration_task_15", wf6.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(12).getStatus());
    }

    @Test
    @DisplayName("Test nested workflow which contains a sub workflow task")
    void testNestedWorkflowWhichContainsASubWorkflowTask() {
        // given: Input for the nested fork join workflow
        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");

        // when: A nested workflow is started with the input
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_NESTED_SUB_WF, 1, "fork_join_nested_test", input, null);

        // then: The workflow is in the running state
        Task nestedSubWfTask =
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
        if (nestedSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, nestedSubWfTask.getTaskId());
        }
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(8, workflow.getTasks().size());
        assertEquals(
                6,
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        List.of("t11", "t12", "t13", "fork1", "fork2", "sw1")
                                                .contains(t.getReferenceTaskName()))
                        .count());
        assertEquals(
                0,
                workflow.getTasks().stream()
                        .filter(t -> List.of("t1", "t2", "t16").contains(t.getReferenceTaskName()))
                        .count());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_11", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("FORK", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_12", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("integration_task_13", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(5).getStatus());
        assertEquals(
                List.of("t14", "t20"), workflow.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(6).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"),
                workflow.getTasks().get(7).getInputData().get("joinOn"));

        // when: Poll and Complete tasks: 'integration_task_11', 'integration_task_12' and
        // 'integration_task_13'
        String outerJoinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join1")
                        .getTaskId();
        String innerJoinId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("join2")
                        .getTaskId();
        Task polledAndAckTask11Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_11", "task11.worker");
        Task polledAndAckTask12Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_12", "task12.worker");
        Task polledAndAckTask13Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_13", "task13.worker");
        workflowExecutionService.getExecutionStatus(workflowInstanceId, true);

        // then: verify that tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask11Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask12Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask13Try1);

        // and: verify the state of the workflow
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(11, wf2.getTasks().size());
        assertEquals("FORK", wf2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(0).getStatus());
        assertEquals("integration_task_11", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(1).getStatus());
        assertEquals("FORK", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(2).getStatus());
        assertEquals("integration_task_12", wf2.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(3).getStatus());
        assertEquals("integration_task_13", wf2.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(4).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf2.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("SUB_WORKFLOW", wf2.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(6).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"), wf2.getTasks().get(7).getInputData().get("joinOn"));
        assertEquals("integration_task_14", wf2.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(8).getStatus());
        assertEquals("DECISION", wf2.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(9).getStatus());
        assertEquals("integration_task_16", wf2.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(10).getStatus());

        // when: Poll and Complete tasks: 'integration_task_16' and 'integration_task_14'
        Task polledAndAckTask16Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_16", "task16.worker");
        Task polledAndAckTask14Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_14", "task14.worker");

        // and: Get the sub workflow id associated with the SubWorkflow Task sw1 and start the
        // system task
        Workflow updatedWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowInstanceId = updatedWorkflow.getTaskByRefName("sw1").getSubWorkflowId();

        // then: verify that tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask16Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask14Try1);
        assertEquals(Workflow.WorkflowStatus.RUNNING, updatedWorkflow.getStatus());
        assertEquals(12, updatedWorkflow.getTasks().size());
        assertEquals("JOIN", updatedWorkflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedWorkflow.getTasks().get(5).getStatus());
        assertEquals(
                List.of("t14", "t20"),
                updatedWorkflow.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("SUB_WORKFLOW", updatedWorkflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedWorkflow.getTasks().get(6).getStatus());
        assertEquals("JOIN", updatedWorkflow.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedWorkflow.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"),
                updatedWorkflow.getTasks().get(7).getInputData().get("joinOn"));
        assertEquals("integration_task_14", updatedWorkflow.getTasks().get(8).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedWorkflow.getTasks().get(8).getStatus());
        assertEquals("DECISION", updatedWorkflow.getTasks().get(9).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedWorkflow.getTasks().get(9).getStatus());
        assertEquals("integration_task_16", updatedWorkflow.getTasks().get(10).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedWorkflow.getTasks().get(10).getStatus());
        assertEquals("integration_task_19", updatedWorkflow.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.SCHEDULED, updatedWorkflow.getTasks().get(11).getStatus());

        // and: verify that the simple Sub Workflow is in running state and the first task is
        // scheduled
        sweep(subWorkflowInstanceId);
        Workflow subWf1 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf1.getStatus());
        assertEquals(1, subWf1.getTasks().size());
        assertEquals("integration_task_1", subWf1.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWf1.getTasks().get(0).getStatus());

        // when: Poll and complete all the tasks associated with the sub workflow
        Task polledAndAckTask1Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_1", "task1.worker");
        Task polledAndAckTask2Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_2", "task2.worker");

        // then: verify that tasks were polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1);
        verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1);

        // and: verify that the simple Sub Workflow is in a COMPLETED state
        Workflow subWf2 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf2.getStatus());
        assertEquals(2, subWf2.getTasks().size());
        assertEquals("integration_task_1", subWf2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subWf2.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", subWf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, subWf2.getTasks().get(1).getStatus());

        // and: verify that the sub workflow task is completed and other preceding tasks are added
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
        assertEquals(12, wf3.getTasks().size());
        assertEquals("JOIN", wf3.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf3.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("SUB_WORKFLOW", wf3.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(6).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"), wf3.getTasks().get(7).getInputData().get("joinOn"));
        assertEquals("integration_task_19", wf3.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf3.getTasks().get(11).getStatus());

        // when: Also the poll and complete the 'integration_task_19'
        Task polledAndAckTask19Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_19", "task19.worker");

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask19Try1);

        // and: verify that the integration_task_19 is completed and the next task is scheduled
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf4.getStatus());
        assertEquals(13, wf4.getTasks().size());
        assertEquals("JOIN", wf4.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf4.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf4.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"), wf4.getTasks().get(7).getInputData().get("joinOn"));
        assertEquals("integration_task_19", wf4.getTasks().get(11).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(11).getStatus());
        assertEquals("integration_task_20", wf4.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(12).getStatus());

        // when: poll and complete the 'integration_task_20'
        Task polledAndAckTask20Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_20", "task20.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask20Try1);

        // when: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // then: verify that the integration_task_20 is completed and the next task is scheduled
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf5.getStatus());
        assertEquals(14, wf5.getTasks().size());
        assertEquals("JOIN", wf5.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(5).getStatus());
        assertEquals(List.of("t14", "t20"), wf5.getTasks().get(5).getInputData().get("joinOn"));
        assertEquals("JOIN", wf5.getTasks().get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(7).getStatus());
        assertEquals(
                List.of("t11", "join2", "sw1"), wf5.getTasks().get(7).getInputData().get("joinOn"));
        assertEquals("integration_task_20", wf5.getTasks().get(12).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(12).getStatus());
        assertEquals("integration_task_15", wf5.getTasks().get(13).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf5.getTasks().get(13).getStatus());

        // when: poll and complete the 'integration_task_15'
        Task polledAndAckTask15Try1 =
                workflowTestUtil.pollAndCompleteTask("integration_task_15", "task15.worker");

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndAckTask15Try1);

        Workflow wf6 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf6.getStatus());
        assertEquals(14, wf6.getTasks().size());
        assertEquals("integration_task_15", wf6.getTasks().get(13).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(13).getStatus());
    }

    @Test
    @DisplayName("Test fork join with sub workflows containing optional tasks")
    void testForkJoinWithSubWorkflowsContainingOptionalTasks() {
        // given: A input to the workflow that has forks of sub workflows with an optional task
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        // when: A workflow that has forks of sub workflows with an optional task is started
        String workflowInstanceId =
                startWorkflow(WORKFLOW_FORK_JOIN_OPTIONAL_SW, 1, "", workflowInput, null);

        // then: verify that the workflow is in a running state
        List<Task> scheduledSubWfTasks =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .toList();
        scheduledSubWfTasks.forEach(
                t -> asyncSystemTaskExecutor.execute(subWorkflowTask, t.getTaskId()));
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());

        // when: both the sub workflows are started by issuing a system task call
        Workflow workflowWithScheduledSubWorkflows =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String joinTaskId =
                workflowWithScheduledSubWorkflows.getTaskByRefName("fanouttask_join").getTaskId();
        String st1SubWfId =
                workflowWithScheduledSubWorkflows.getTaskByRefName("st1").getSubWorkflowId();
        String st2SubWfId =
                workflowWithScheduledSubWorkflows.getTaskByRefName("st2").getSubWorkflowId();
        sweep(st1SubWfId);
        sweep(st2SubWfId);

        // then: verify that the sub workflow tasks are in a IN PROGRESS state
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(4, wf2.getTasks().size());
        assertEquals("SUB_WORKFLOW", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(1).getStatus());
        assertEquals("SUB_WORKFLOW", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "st2"), wf2.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());

        // and: Also verify that the sub workflows are in a RUNNING state
        Workflow workflowWithRunningSubWorkflows =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowInstanceId1 =
                workflowWithRunningSubWorkflows.getTaskByRefName("st1").getSubWorkflowId();
        Workflow subWf1 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId1, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf1.getStatus());
        assertEquals(1, subWf1.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, subWf1.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf1.getTasks().get(0).getTaskType());

        String subWorkflowInstanceId2 =
                workflowWithRunningSubWorkflows.getTaskByRefName("st2").getSubWorkflowId();
        Workflow subWf2 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId2, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf2.getStatus());
        assertEquals(1, subWf2.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, subWf2.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf2.getTasks().get(0).getTaskType());

        // when: The 'simple_task_in_sub_wf' belonging to both the sub workflows is polled and
        // failed
        Task polledAndAckSubWorkflowTask1 =
                workflowTestUtil.pollAndFailTask(
                        "simple_task_in_sub_wf", "task1.worker", "Failed....");
        Task polledAndAckSubWorkflowTask2 =
                workflowTestUtil.pollAndFailTask(
                        "simple_task_in_sub_wf", "task1.worker", "Failed....");

        // then: verify that both the tasks were polled and failed
        verifyPolledAndAcknowledgedTask(polledAndAckSubWorkflowTask1);
        verifyPolledAndAcknowledgedTask(polledAndAckSubWorkflowTask2);

        // and: verify that both the sub workflows are in failed state
        Workflow subWf1Failed =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId1, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, subWf1Failed.getStatus());
        assertEquals(1, subWf1Failed.getTasks().size());
        assertEquals(Task.Status.FAILED, subWf1Failed.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf1Failed.getTasks().get(0).getTaskType());

        Workflow subWf2Failed =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId2, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, subWf2Failed.getStatus());
        assertEquals(1, subWf2Failed.getTasks().size());
        assertEquals(Task.Status.FAILED, subWf2Failed.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf2Failed.getTasks().get(0).getTaskType());
        sweep(workflowInstanceId);

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: verify that the workflow is in a COMPLETED state and the join task is also marked
        // as COMPLETED_WITH_ERRORS
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf3.getStatus());
        assertEquals(4, wf3.getTasks().size());
        assertEquals("FORK", wf3.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf3.getTasks().get(1).getStatus());
        assertEquals("SUB_WORKFLOW", wf3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf3.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf3.getTasks().get(3).getStatus());

        // when: do a rerun on the sub workflow
        RerunWorkflowRequest reRunSubWorkflowRequest = new RerunWorkflowRequest();
        reRunSubWorkflowRequest.setReRunFromWorkflowId(subWorkflowInstanceId1);
        workflowExecutor.rerun(reRunSubWorkflowRequest);

        // then: verify that the sub workflows are in a RUNNING state
        Workflow subWf1Rerun =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId1, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf1Rerun.getStatus());
        assertEquals(1, subWf1Rerun.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, subWf1Rerun.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf1Rerun.getTasks().get(0).getTaskType());

        // and: parent workflow remains the same
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf4.getStatus());
        assertEquals(4, wf4.getTasks().size());
        assertEquals("FORK", wf4.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf4.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf4.getTasks().get(1).getStatus());
        assertEquals("SUB_WORKFLOW", wf4.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf4.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, wf4.getTasks().get(3).getStatus());
    }

    @Test
    @DisplayName("Test fork join with sub workflow task using task definition")
    void testForkJoinWithSubWorkflowTaskUsingTaskDefinition() {
        // given: A input to the workflow that has fork with sub workflow task
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");

        // when: A workflow that has fork with sub workflow task is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_SUB_WORKFLOW, 1, "", workflowInput, null);

        // then: verify that the workflow is in a RUNNING state
        Task forkSubWfTask =
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
        if (forkSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, forkSubWfTask.getTaskId());
        }
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), workflow.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(3).getStatus());

        // when: the subworkflow is started by issuing a system task call
        Workflow parentWorkflow =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowTaskId = parentWorkflow.getTaskByRefName("st1").getTaskId();
        String jointaskId = parentWorkflow.getTaskByRefName("fanouttask_join").getTaskId();

        // then: verify that the sub workflow task is in a IN_PROGRESS state
        Workflow wf2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf2.getStatus());
        assertEquals(4, wf2.getTasks().size());
        assertEquals("FORK", wf2.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf2.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf2.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", wf2.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf2.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf2.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), wf2.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, wf2.getTasks().get(3).getStatus());

        // when: sub workflow is retrieved
        Workflow workflowForSubId =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String subWorkflowInstanceId = workflowForSubId.getTaskByRefName("st1").getSubWorkflowId();
        sweep(subWorkflowInstanceId);

        // then: verify that the sub workflow is in a RUNNING state
        Workflow subWf1 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf1.getStatus());
        assertEquals(1, subWf1.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, subWf1.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf1.getTasks().get(0).getTaskType());

        // when: the 'simple_task_in_sub_wf' belonging to the sub workflow is polled and failed
        Task polledAndFailSubWorkflowTask =
                workflowTestUtil.pollAndFailTask(
                        "simple_task_in_sub_wf", "task1.worker", "Failed....");

        // then: verify that the task was polled and failed
        verifyPolledAndAcknowledgedTask(polledAndFailSubWorkflowTask);

        // and: verify that the sub workflow is in failed state
        Workflow subWfFailed =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, subWfFailed.getStatus());
        assertEquals(1, subWfFailed.getTasks().size());
        assertEquals(Task.Status.FAILED, subWfFailed.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWfFailed.getTasks().get(0).getTaskType());

        // and: verify that the workflow is in a RUNNING state and sub workflow task is retried
        sweep(workflowInstanceId);
        // The background sweeper may have already consumed the retry task from the queue.
        // Look up the retry task directly from workflow state and start it only if still SCHEDULED.
        Task retriedTask =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getRetryCount() == 1)
                        .findFirst()
                        .orElse(null);
        if (retriedTask != null && retriedTask.getStatus() == Task.Status.SCHEDULED) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, retriedTask.getTaskId());
        }
        Workflow wf3 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf3.getStatus());
        assertEquals(5, wf3.getTasks().size());
        assertEquals("FORK", wf3.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf3.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf3.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf3.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", wf3.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf3.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf3.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), wf3.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(3).getStatus());
        assertEquals("SUB_WORKFLOW", wf3.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf3.getTasks().get(4).getStatus());

        // when: the sub workflow is started by issuing a system task call
        parentWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        subWorkflowTaskId = parentWorkflow.getTaskByRefName("st1").getTaskId();

        // then: verify that the sub workflow task is in a IN PROGRESS state
        Workflow wf4 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf4.getStatus());
        assertEquals(5, wf4.getTasks().size());
        assertEquals("FORK", wf4.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf4.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf4.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf4.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", wf4.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf4.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf4.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), wf4.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(3).getStatus());
        assertEquals("SUB_WORKFLOW", wf4.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, wf4.getTasks().get(4).getStatus());

        // when: sub workflow is retrieved
        Workflow workflowForSubId2 =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        subWorkflowInstanceId = workflowForSubId2.getTaskByRefName("st1").getSubWorkflowId();
        sweep(subWorkflowInstanceId);

        // then: verify that the sub workflow is in a RUNNING state
        Workflow subWf2 = workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf2.getStatus());
        assertEquals(1, subWf2.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, subWf2.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWf2.getTasks().get(0).getTaskType());

        // when: the 'simple_task_in_sub_wf' belonging to the sub workflow is polled and completed
        Task polledAndCompletedSubWorkflowTask =
                workflowTestUtil.pollAndCompleteTask(
                        "simple_task_in_sub_wf", "subworkflow.task.worker");

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedSubWorkflowTask);

        // and: verify that the sub workflow is in COMPLETED state
        Workflow subWfCompleted =
                workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWfCompleted.getStatus());
        assertEquals(1, subWfCompleted.getTasks().size());
        assertEquals(Task.Status.COMPLETED, subWfCompleted.getTasks().get(0).getStatus());
        assertEquals("simple_task_in_sub_wf", subWfCompleted.getTasks().get(0).getTaskType());

        // and: verify that the workflow is in a RUNNING state and sub workflow task is completed
        Workflow wf5 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf5.getStatus());
        assertEquals(5, wf5.getTasks().size());
        assertEquals("FORK", wf5.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf5.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf5.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", wf5.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, wf5.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf5.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), wf5.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, wf5.getTasks().get(3).getStatus());
        assertEquals("SUB_WORKFLOW", wf5.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf5.getTasks().get(4).getStatus());

        // when: the simple task is polled and completed
        Task polledAndCompletedSimpleTask =
                workflowTestUtil.pollAndCompleteTask("integration_task_2", "task2.worker");

        // and: workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that the task was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedSimpleTask);

        // when: JOIN task is executed
        asyncSystemTaskExecutor.execute(joinTask, jointaskId);

        // then: verify that the workflow is in a COMPLETED state
        Workflow wf6 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf6.getStatus());
        assertEquals(5, wf6.getTasks().size());
        assertEquals("FORK", wf6.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(0).getStatus());
        assertEquals("SUB_WORKFLOW", wf6.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf6.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", wf6.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(2).getStatus());
        assertEquals("JOIN", wf6.getTasks().get(3).getTaskType());
        assertEquals(List.of("st1", "t2"), wf6.getTasks().get(3).getInputData().get("joinOn"));
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(3).getStatus());
        assertEquals("SUB_WORKFLOW", wf6.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf6.getTasks().get(4).getStatus());
    }
}
