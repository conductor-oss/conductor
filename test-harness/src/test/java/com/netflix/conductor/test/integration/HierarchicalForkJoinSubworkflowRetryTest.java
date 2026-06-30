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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class HierarchicalForkJoinSubworkflowRetryTest extends AbstractSpecification {

    private static final String FORK_JOIN_HIERARCHICAL_SUB_WF = "hierarchical_fork_join_swf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired private QueueDAO queueDAO;

    @Autowired private SubWorkflow subWorkflowTask;

    @Autowired private Join joinTask;

    private String rootWorkflowId;
    private String midLevelWorkflowId;
    private String leafWorkflowId;

    private TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() throws Exception {
        workflowTestUtil.registerWorkflows(
                "hierarchical_fork_join_swf.json", "simple_workflow_1_integration_test.json");

        // setup: Modify task definition to 0 retries
        persistedTask2Definition =
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

        // and: an existing workflow with subworkflow and registered definitions
        metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1);
        metadataService.getWorkflowDef(FORK_JOIN_HIERARCHICAL_SUB_WF, 1);

        // and: input required to start the workflow execution
        String correlationId = "retry_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", FORK_JOIN_HIERARCHICAL_SUB_WF);
        input.put("nextSubwf", SIMPLE_WORKFLOW);

        // when: the workflow is started
        rootWorkflowId =
                startWorkflow(FORK_JOIN_HIERARCHICAL_SUB_WF, 1, correlationId, input, null);

        // then: verify that the workflow is in a RUNNING state
        List<String> polledTaskIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200);
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds.get(0));
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // when: poll and complete the integration_task_2 task
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task2.done");
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker", task2Output);

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // then: verify that the 'sub_workflow_task' is in a IN_PROGRESS state
        Workflow rootWorkflowInstance =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflowInstance.getStatus());
        assertEquals(4, rootWorkflowInstance.getTasks().size());

        // and: sweep the mid-level workflow so its tasks get scheduled
        midLevelWorkflowId = rootWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(midLevelWorkflowId);

        // when: poll and complete the integration_task_2 task in the mid-level workflow
        Map<String, Object> task2OutputMid = new HashMap<>();
        task2OutputMid.put("op", "task2.done");
        Task pollAndCompleteTask2 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task2.integration.worker", task2OutputMid);

        // then: verify that the 'integration_task_2' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2);

        // and: start the exact mid-level SUB_WORKFLOW task to avoid re-executing a stale parent
        // queue item
        String midLevelSubWorkflowTaskId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getTaskId();
        asyncSystemTaskExecutor.execute(subWorkflowTask, midLevelSubWorkflowTaskId);
        Workflow midLevelWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midLevelWf.getStatus());
        assertEquals(4, midLevelWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midLevelWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midLevelWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midLevelWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midLevelWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midLevelWf.getTasks().get(3).getStatus());

        // and: get the leaf workflow from the mid-level workflow and sweep it
        Workflow midLevelWorkflowInstance =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        leafWorkflowId = midLevelWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(leafWorkflowId);

        // then: verify that the leaf workflow is RUNNING, and first task is in SCHEDULED state
        Workflow leafWorkflowInstance =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWorkflowInstance.getStatus());
        assertEquals(1, leafWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", leafWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWorkflowInstance.getTasks().get(0).getStatus());

        // when: poll and fail the integration_task_2 task
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", task1Output);
        sweep(leafWorkflowId);
        workflowTestUtil.pollAndFailTask(
                "integration_task_2", "task2.integration.worker", "failed");

        // then: the leaf workflow ends up in a FAILED state
        Workflow failedLeafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedLeafWf.getStatus());
        assertEquals(2, failedLeafWf.getTasks().size());
        assertEquals("integration_task_1", failedLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", failedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedLeafWf.getTasks().get(1).getStatus());

        // when: the mid level workflow is 'decided'
        sweep(midLevelWorkflowId);

        // then: the mid level workflow is in FAILED state
        Workflow failedMidWf =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedMidWf.getStatus());
        assertEquals(4, failedMidWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, failedMidWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedMidWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, failedMidWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedMidWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", failedMidWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedMidWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, failedMidWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.CANCELED, failedMidWf.getTasks().get(3).getStatus());

        // when: the root level workflow is 'decided'
        sweep(rootWorkflowId);

        // then: the root level workflow is in FAILED state
        Workflow failedRootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedRootWf.getStatus());
        assertEquals(4, failedRootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, failedRootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedRootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, failedRootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedRootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", failedRootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedRootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, failedRootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.CANCELED, failedRootWf.getTasks().get(3).getStatus());
    }

    @AfterEach
    void restoreTaskDef() {
        if (persistedTask2Definition != null) {
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the root workflow.
     *
     * <p>Expectation: The root workflow spawns a NEW mid-level workflow, which in turn spawns a NEW
     * leaf workflow. When the leaf workflow completes successfully, both the NEW mid-level and root
     * workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the root in a 3-level subworkflow")
    void testRetryOnTheRootIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the root workflow
        workflowExecutor.retry(rootWorkflowId, false);
        List<String> polledRetriedRootSubWorkflowIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200);
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledRetriedRootSubWorkflowIds.get(0));

        // then: verify that the root workflow created a new SUB_WORKFLOW task
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(5, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootWf.getTasks().get(1).getStatus());
        assertTrue(rootWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(4).getStatus());
        assertEquals(
                rootWf.getTasks().get(1).getTaskId(), rootWf.getTasks().get(4).getRetriedTaskId());

        // when: the subworkflow task should be in SCHEDULED state and is started by issuing a
        // system task call
        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(4)
                        .getSubWorkflowId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        sweep(newMidLevelWorkflowId);
        List<String> polledNewMidSubWorkflowIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200);
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledNewMidSubWorkflowIds.get(0));

        // then: verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(midLevelWorkflowId, newMidLevelWorkflowId);
        Workflow newMidWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidWf.getStatus());
        assertEquals(4, newMidWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, newMidWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, newMidWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", newMidWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newMidWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, newMidWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidWf.getTasks().get(3).getStatus());

        // when: poll and complete the integration_task_1 task in the mid-level workflow
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task2.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", task2Output);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // when: poll and complete the two tasks in the leaf workflow
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", task1Output);
        Map<String, Object> task2OutputLeaf = new HashMap<>();
        task2OutputLeaf.put("op", "task2.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", task2OutputLeaf);

        // then: the new leaf workflow is in COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals(2, completedLeafWf.getTasks().size());
        assertEquals("integration_task_1", completedLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(1).getStatus());

        // when: the new mid level and root workflows are 'decided'
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: the new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(newMidLevelWorkflowId);

        // then: the root workflow is in COMPLETED state
        assertSubWorkflowTaskIsRetriedAndWorkflowCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the root workflow.
     *
     * <p>Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent
     * (root) workflows are also retried. When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the mid-level in a 3-level subworkflow")
    void testRetryOnTheMidLevelIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the mid level workflow
        workflowExecutor.retry(midLevelWorkflowId, false);
        List<String> polledRetriedMidSubWorkflowIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200);
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledRetriedMidSubWorkflowIds.get(0));

        // then: verify that the mid workflow created a new SUB_WORKFLOW task
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(5, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midWf.getTasks().get(1).getStatus());
        assertTrue(midWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(4).getStatus());
        assertEquals(
                midWf.getTasks().get(1).getTaskId(), midWf.getTasks().get(4).getRetriedTaskId());

        // and: verify the SUB_WORKFLOW task in root workflow is IN_PROGRESS state
        // retry() updates parents asynchronously via the decider queue; the background
        // sweeper re-decides the JOIN from CANCELED to IN_PROGRESS (same sweeper race as #1047).
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(rootWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // when: the SUB_WORKFLOW task in mid level workflow is started by issuing a system task
        // call
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(4)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // when: poll and complete the 2 tasks in the leaf workflow
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", task1Output);
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", task2Output);

        // then: verify that the new leaf workflow reached COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals(2, completedLeafWf.getTasks().size());
        assertEquals("integration_task_1", completedLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: verify that the mid level and root workflows reach COMPLETED state
        assertSubWorkflowTaskIsRetriedAndWorkflowCompleted(midLevelWorkflowId);
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the mid-level workflow.
     *
     * <p>Expectation: The mid-level workflow spawns a NEW leaf workflow and also updates its parent
     * (root workflow). When the NEW leaf workflow completes successfully, both the mid-level and
     * root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the leaf in a 3-level subworkflow")
    void testRetryOnTheLeafIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the leaf workflow
        workflowExecutor.retry(leafWorkflowId, false);

        // then: verify that the leaf workflow is in RUNNING state and failed task is retried
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(3, leafWf.getTasks().size());
        assertEquals("integration_task_1", leafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWf.getTasks().get(1).getStatus());
        assertTrue(leafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(2).getStatus());
        assertEquals(
                leafWf.getTasks().get(1).getTaskId(), leafWf.getTasks().get(2).getRetriedTaskId());

        // then: verify that the mid-level workflow's SUB_WORKFLOW task is updated
        // retry() updates parents asynchronously via the decider queue; the background
        // sweeper re-decides the JOIN from CANCELED to IN_PROGRESS (same sweeper race as #1047).
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(midLevelWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // and: verify that the root workflow's SUB_WORKFLOW task is updated
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(rootWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // then: verify that the mid-level workflow's JOIN task is updated
        Workflow midWfAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWfAfterSweep.getStatus());
        assertEquals(4, midWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(midWfAfterSweep.getTasks().get(1).isSubworkflowChanged()); // flag is reset
        assertEquals("integration_task_2", midWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(3).getStatus());

        // and: verify that the root workflow's JOIN task is updated
        Workflow rootWfAfterSweep =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWfAfterSweep.getStatus());
        assertEquals(4, rootWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(rootWfAfterSweep.getTasks().get(1).isSubworkflowChanged()); // flag is reset
        assertEquals("integration_task_2", rootWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(3).getStatus());

        // when: poll and complete the scheduled task in the leaf workflow
        Map<String, Object> task1Output = new HashMap<>();
        task1Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", task1Output);

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals(3, completedLeafWf.getTasks().size());
        assertEquals("integration_task_1", completedLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, completedLeafWf.getTasks().get(1).getStatus());
        assertTrue(completedLeafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(2).getStatus());
        assertEquals(
                completedLeafWf.getTasks().get(1).getTaskId(),
                completedLeafWf.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: verify that the mid level and root workflows reach COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the root workflow.
     *
     * <p>Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent
     * (root) workflows are also retried. When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the root with resume flag in a 3-level subworkflow")
    void testRetryOnTheRootWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the root workflow
        workflowExecutor.retry(rootWorkflowId, true);

        // then: verify that the sub workflow task in root workflow is IN_PROGRESS state
        // retry() updates parents asynchronously via the decider queue; the background
        // sweeper re-decides the JOIN from CANCELED to IN_PROGRESS (same sweeper race as #1047).
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(rootWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // and: verify that the sub workflow task in mid level workflow is IN_PROGRESS state
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(midLevelWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // and: verify that the previously failed task in leaf workflow is in SCHEDULED state
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(3, leafWf.getTasks().size());
        assertEquals("integration_task_2", leafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWf.getTasks().get(1).getStatus());
        assertTrue(leafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(2).getStatus());
        assertEquals(
                leafWf.getTasks().get(1).getTaskId(), leafWf.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // then: verify the mid level workflow's JOIN is updated
        Workflow midWfAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWfAfterSweep.getStatus());
        assertEquals(4, midWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(midWfAfterSweep.getTasks().get(1).isSubworkflowChanged()); // flag is reset
        assertEquals("integration_task_2", midWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(3).getStatus());

        // and: verify the root workflow's JOIN is updated
        Workflow rootWfAfterSweep =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWfAfterSweep.getStatus());
        assertEquals(4, rootWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(rootWfAfterSweep.getTasks().get(1).isSubworkflowChanged()); // flag is reset
        assertEquals("integration_task_2", rootWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(3).getStatus());

        // when: poll and complete the integration_task_2 task
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", task2Output);

        // then: verify that the leaf workflow is in COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals(3, completedLeafWf.getTasks().size());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, completedLeafWf.getTasks().get(1).getStatus());
        assertTrue(completedLeafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(2).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: the new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);

        // and: the root workflow is in COMPLETED state
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the mid-level workflow.
     *
     * <p>Expectation: The leaf workflow resumes its FAILED task and updates both its parent
     * (mid-level) and grandparent (root). When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the mid-level with resume flag in a 3-level subworkflow")
    void testRetryOnTheMidLevelWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the root workflow
        workflowExecutor.retry(midLevelWorkflowId, true);

        // then: verify that the sub workflow task in root workflow is updated
        // retry() updates parents asynchronously via the decider queue; the background
        // sweeper re-decides the JOIN from CANCELED to IN_PROGRESS (same sweeper race as #1047).
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(rootWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // and: verify that the sub workflow task in mid level workflow is updated
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(midLevelWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // and: verify that the previously failed task in leaf workflow is in SCHEDULED state
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(3, leafWf.getTasks().size());
        assertEquals("integration_task_2", leafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWf.getTasks().get(1).getStatus());
        assertTrue(leafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(2).getStatus());
        assertEquals(
                leafWf.getTasks().get(1).getTaskId(), leafWf.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // then: verify the mid level workflow's JOIN is updated
        Workflow midWfAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWfAfterSweep.getStatus());
        assertEquals(4, midWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                midWfAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", midWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(3).getStatus());

        // and: verify the root workflow's JOIN is updated
        Workflow rootWfAfterSweep =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWfAfterSweep.getStatus());
        assertEquals(4, rootWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                rootWfAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", rootWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(3).getStatus());

        // when: poll and complete the previously failed integration_task_2 task
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task1.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", task2Output);

        // then: verify that the leaf workflow is in COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals(3, completedLeafWf.getTasks().size());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, completedLeafWf.getTasks().get(1).getStatus());
        assertTrue(completedLeafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(2).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: the new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);

        // and: the root workflow is in COMPLETED state
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the leaf workflow.
     *
     * <p>Expectation: The leaf workflow resumes its FAILED task and updates both its parent
     * (mid-level) and grandparent (root). When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the leaf with resume flag in a 3-level subworkflow")
    void testRetryOnTheLeafWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the leaf workflow
        workflowExecutor.retry(leafWorkflowId, true);

        // then: verify that the leaf workflow is in RUNNING state and failed task is retried
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(3, leafWf.getTasks().size());
        assertEquals("integration_task_2", leafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWf.getTasks().get(1).getStatus());
        assertTrue(leafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(2).getStatus());
        assertEquals(
                leafWf.getTasks().get(1).getTaskId(), leafWf.getTasks().get(2).getRetriedTaskId());

        // then: verify that the mid-level workflow is updated
        // retry() updates parents asynchronously via the decider queue; the background
        // sweeper re-decides the JOIN from CANCELED to IN_PROGRESS (same sweeper race as #1047).
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(midLevelWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // and: verify that the root workflow is updated
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(rootWorkflowId, true)
                                                .getTasks()
                                                .get(3)
                                                .getStatus()
                                        == Task.Status.IN_PROGRESS);
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // then: verify the mid level workflow's JOIN is updated
        Workflow midWfAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWfAfterSweep.getStatus());
        assertEquals(4, midWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                midWfAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", midWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWfAfterSweep.getTasks().get(3).getStatus());

        // and: verify the root workflow's JOIN is updated
        Workflow rootWfAfterSweep =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWfAfterSweep.getStatus());
        assertEquals(4, rootWfAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWfAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                rootWfAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", rootWfAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWfAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWfAfterSweep.getTasks().get(3).getStatus());

        // when: poll and complete the scheduled task in the leaf workflow
        Map<String, Object> task2Output = new HashMap<>();
        task2Output.put("op", "task2.done");
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", task2Output);

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow completedLeafWf =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeafWf.getStatus());
        assertEquals("integration_task_1", completedLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, completedLeafWf.getTasks().get(1).getStatus());
        assertTrue(completedLeafWf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", completedLeafWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeafWf.getTasks().get(2).getStatus());
        assertEquals(
                completedLeafWf.getTasks().get(1).getTaskId(),
                completedLeafWf.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // and: JOIN tasks are executed
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // then: the new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);

        // and: the root workflow is in COMPLETED state
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    private void assertWorkflowIsCompleted(String workflowId) {
        Workflow wf = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
        assertEquals(4, wf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(1).getStatus());
        assertFalse(wf.getTasks().get(1).isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", wf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, wf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(3).getStatus());
    }

    private void assertSubWorkflowTaskIsRetriedAndWorkflowCompleted(String workflowId) {
        Workflow wf = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
        assertEquals(5, wf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, wf.getTasks().get(1).getStatus());
        assertTrue(wf.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", wf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, wf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(3).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(4).getStatus());
        assertEquals(wf.getTasks().get(1).getTaskId(), wf.getTasks().get(4).getRetriedTaskId());
    }
}
