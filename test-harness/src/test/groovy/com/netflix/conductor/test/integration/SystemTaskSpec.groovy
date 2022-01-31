/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.test.integration

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.UserTask

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class SystemTaskSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    UserTask userTask

    @Shared
    def ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW = 'async_complete_integration_test_wf'

    def setup() {
        workflowTestUtil.registerWorkflows('simple_workflow_with_async_complete_system_task_integration_test.json')
    }

    def "Test system task with asyncComplete set to true"() {

        given: "An existing workflow definition with async complete system task"
        metadataService.getWorkflowDef(ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW, 1)

        and: "input required to start the workflow"
        String correlationId = 'async_complete_test' + UUID.randomUUID()
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(ASYNC_COMPLETE_SYSTEM_TASK_WORKFLOW, 1,
                correlationId, input, null, null, null)

        then: "ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task"
        def pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        and: "verify that the 'integration_task1' is complete and the next task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'USER_TASK'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the system task is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("USER_TASK", 1, 200)
        asyncSystemTaskExecutor.execute(userTask, polledTaskIds[0])

        then: "verify that the system task is in IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == "USER_TASK"
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        when: "sweeper evaluates the workflow"
        sweep(workflowInstanceId)

        then: "workflow state is unchanged"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == "USER_TASK"
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        when: "result of the user task is curated"
        Task task = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).getTaskByRefName('user_task')
        def taskResult = new TaskResult(task)
        taskResult.status = TaskResult.Status.COMPLETED
        taskResult.outputData['op'] = 'user.task.done'

        and: "external signal is simulated with this output to complete the system task"
        workflowExecutor.updateTask(taskResult)

        then: "ensure that the system task is COMPLETED and workflow is COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'USER_TASK'
            tasks[1].status == Task.Status.COMPLETED
        }
    }
}
