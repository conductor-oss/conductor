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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;
import com.netflix.conductor.test.utils.UserTask;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class SystemTaskTest extends AbstractSpecification {

    @Autowired QueueDAO queueDAO;

    @Autowired UserTask userTask;

    private static final String ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW =
            "async_complete_integration_test_wf";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_workflow_with_async_complete_system_task_integration_test.json");
    }

    @Test
    @DisplayName("Test system task with asyncComplete set to true")
    void testSystemTaskWithAsyncCompleteSetToTrue() throws Exception {
        // given: An existing workflow definition with async complete system task
        metadataService.getWorkflowDef(ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW, 1);

        // and: input required to start the workflow
        String correlationId = "async_complete_test" + UUID.randomUUID();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        // when: the workflow is started
        String workflowInstanceId =
                startWorkflow(ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW, 1, correlationId, input, null);

        // then: ensure that the workflow has started
        var workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: poll and complete the integration_task_1 task
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // and: verify that the 'integration_task1' is complete and the next task is in SCHEDULED
        // state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: the system task is started by issuing a system task call
        List<String> polledTaskIds = queueDAO.pop("USER_TASK", 1, 200);
        asyncSystemTaskExecutor.execute(userTask, polledTaskIds.get(0));

        // then: verify that the system task is in IN_PROGRESS state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());

        // when: sweeper evaluates the workflow
        sweep(workflowInstanceId);

        // then: workflow state is unchanged
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());

        // when: result of the user task is curated
        Task task =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("user_task");
        TaskResult taskResult = new TaskResult(task);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().put("op", "user.task.done");

        // and: external signal is simulated with this output to complete the system task
        workflowExecutor.updateTask(taskResult);

        // then: ensure that the system task is COMPLETED and workflow is COMPLETED
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("USER_TASK", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
    }
}
