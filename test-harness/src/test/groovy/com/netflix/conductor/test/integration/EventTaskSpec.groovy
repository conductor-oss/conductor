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
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.Event
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class EventTaskSpec extends AbstractSpecification {

    def EVENT_BASED_WORKFLOW = 'test_event_workflow'

    @Autowired
    Event eventTask

    @Autowired
    QueueDAO queueDAO

    def setup() {
        workflowTestUtil.registerWorkflows('event_workflow_integration_test.json')
    }

    def "Verify that a event based simple workflow is executed"() {
        when: "Start a event based workflow"
        def workflowInstanceId = startWorkflow(EVENT_BASED_WORKFLOW, 1,
                '', [:], null)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['event_produced']
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The integration_task_1 is polled and completed"
        def polledAndCompletedTry1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task was polled and completed and the workflow is in a complete state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.COMPLETED
        }
    }

    def "Test a workflow with event task that is asyncComplete "() {
        setup: "Register a workflow definition with event task as asyncComplete"
        def persistedWorkflowDefinition = metadataService.getWorkflowDef(EVENT_BASED_WORKFLOW, 1)
        def modifiedWorkflowDefinition = new WorkflowDef()
        modifiedWorkflowDefinition.name = persistedWorkflowDefinition.name
        modifiedWorkflowDefinition.version = persistedWorkflowDefinition.version
        modifiedWorkflowDefinition.tasks = persistedWorkflowDefinition.tasks
        modifiedWorkflowDefinition.inputParameters = persistedWorkflowDefinition.inputParameters
        modifiedWorkflowDefinition.outputParameters = persistedWorkflowDefinition.outputParameters
        modifiedWorkflowDefinition.ownerEmail = persistedWorkflowDefinition.ownerEmail
        modifiedWorkflowDefinition.tasks[0].asyncComplete = true
        metadataService.updateWorkflowDef([modifiedWorkflowDefinition])

        when: "The event task workflow is started"
        def workflowInstanceId = startWorkflow(EVENT_BASED_WORKFLOW, 1,
                '', [:], null)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[0].outputData['event_produced']
        }

        when: "The event task is updated async using the API"
        Task task = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).getTaskByRefName('wait0')
        TaskResult taskResult = new TaskResult(task)
        taskResult.setStatus(TaskResult.Status.COMPLETED)
        workflowExecutor.updateTask(taskResult)

        then: "Ensure that event task is COMPLETED and workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['event_produced']
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The integration_task_1 is polled and completed"
        def polledAndCompletedTry1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task was polled and completed and the workflow is in a complete state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['event_produced']
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.COMPLETED
        }

        cleanup: "Ensure that the changes to the workflow def are reverted"
        metadataService.updateWorkflowDef([persistedWorkflowDefinition])
    }
}
