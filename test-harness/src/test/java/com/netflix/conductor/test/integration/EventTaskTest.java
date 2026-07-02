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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Event;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class EventTaskTest extends AbstractSpecification {

    private static final String EVENT_BASED_WORKFLOW = "test_event_workflow";

    @Autowired Event eventTask;

    @Autowired QueueDAO queueDAO;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows("event_workflow_integration_test.json");
    }

    @Test
    @DisplayName("Verify that a event based simple workflow is executed")
    void verifyEventBasedSimpleWorkflowIsExecuted() {
        // when: Start a event based workflow
        String workflowInstanceId =
                startWorkflow(EVENT_BASED_WORKFLOW, 1, "", new HashMap<>(), null);

        // then: Retrieve the workflow
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(TaskType.EVENT.name(), workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertNotNull(workflow.getTasks().get(0).getOutputData().get("event_produced"));
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: The integration_task_1 is polled and completed
        Task polledAndCompletedTry1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");

        // then: verify that the task was polled and completed and the workflow is in a complete
        // state
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1);
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
    }

    @Test
    @DisplayName("Test a workflow with event task that is asyncComplete")
    void testWorkflowWithEventTaskThatIsAsyncComplete() {
        // setup: Register a workflow definition with event task as asyncComplete
        WorkflowDef persistedWorkflowDefinition =
                metadataService.getWorkflowDef(EVENT_BASED_WORKFLOW, 1);
        WorkflowDef modifiedWorkflowDefinition = new WorkflowDef();
        modifiedWorkflowDefinition.setName(persistedWorkflowDefinition.getName());
        modifiedWorkflowDefinition.setVersion(persistedWorkflowDefinition.getVersion());
        modifiedWorkflowDefinition.setTasks(persistedWorkflowDefinition.getTasks());
        modifiedWorkflowDefinition.setInputParameters(
                persistedWorkflowDefinition.getInputParameters());
        modifiedWorkflowDefinition.setOutputParameters(
                persistedWorkflowDefinition.getOutputParameters());
        modifiedWorkflowDefinition.setOwnerEmail(persistedWorkflowDefinition.getOwnerEmail());
        modifiedWorkflowDefinition.getTasks().get(0).setAsyncComplete(true);
        metadataService.updateWorkflowDef(List.of(modifiedWorkflowDefinition));

        try {
            // when: The event task workflow is started
            String workflowInstanceId =
                    startWorkflow(EVENT_BASED_WORKFLOW, 1, "", new HashMap<>(), null);

            // then: Retrieve the workflow
            Workflow workflow =
                    workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertEquals(TaskType.EVENT.name(), workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
            assertNotNull(workflow.getTasks().get(0).getOutputData().get("event_produced"));

            // when: The event task is updated async using the API
            Task task =
                    workflowExecutionService
                            .getExecutionStatus(workflowInstanceId, true)
                            .getTaskByRefName("wait0");
            TaskResult taskResult = new TaskResult(task);
            taskResult.setStatus(TaskResult.Status.COMPLETED);
            workflowExecutor.updateTask(taskResult);

            // then: Ensure that event task is COMPLETED and workflow has progressed
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals(TaskType.EVENT.name(), workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertNotNull(workflow.getTasks().get(0).getOutputData().get("event_produced"));
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

            // when: The integration_task_1 is polled and completed
            Task polledAndCompletedTry1 =
                    workflowTestUtil.pollAndCompleteTask(
                            "integration_task_1", "task1.integration.worker");

            // then: verify that the task was polled and completed and the workflow is in a complete
            // state
            verifyPolledAndAcknowledgedTask(polledAndCompletedTry1);
            workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(2, workflow.getTasks().size());
            assertEquals(TaskType.EVENT.name(), workflow.getTasks().get(0).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
            assertNotNull(workflow.getTasks().get(0).getOutputData().get("event_produced"));
            assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
            assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        } finally {
            // cleanup: Ensure that the changes to the workflow def are reverted
            metadataService.updateWorkflowDef(List.of(persistedWorkflowDefinition));
        }
    }
}
