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
package com.netflix.conductor.test.resiliency

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.reconciliation.WorkflowRepairService
import com.netflix.conductor.test.base.AbstractResiliencySpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class TaskResiliencySpec extends AbstractResiliencySpecification {

    @Autowired
    WorkflowRepairService workflowRepairService

    @Shared
    def SIMPLE_TWO_TASK_WORKFLOW = 'integration_test_wf'

    def setup() {
        workflowTestUtil.taskDefinitions()
        workflowTestUtil.registerWorkflows(
                'simple_workflow_1_integration_test.json'
        )
    }

    def "Verify that a workflow recovers and completes on schedule task failure from queue push failure"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow(SIMPLE_TWO_TASK_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "Retrieve the workflow"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.size() == 1
        workflow.tasks[0].taskType == 'integration_task_1'
        workflow.tasks[0].status == Task.Status.SCHEDULED
        def taskId = workflow.tasks[0].taskId

        // Simulate queue push failure when creating a new task, after completing first task
        when: "The first task 'integration_task_1' is polled and completed"
        def task1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "Verify that the task was polled and acknowledged"
        1 * queueDAO.pop(_, 1, _) >> Collections.singletonList(taskId)
        1 * queueDAO.ack(*_) >> true
        1 * queueDAO.push(*_) >> { throw new IllegalStateException("Queue push failed from Spy") }
        verifyPolledAndAcknowledgedTask(task1Try1)

        and: "Ensure that the next task is SCHEDULED even after failing to push taskId message to queue"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The second task 'integration_task_2' is polled for"
        def task1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "Verify that the task was not polled, and the taskId doesn't exist in the queue"
        task1Try2[0] == null
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
            def currentTaskId = tasks[1].getTaskId()
            !queueDAO.containsMessage("integration_task_2", currentTaskId)
        }

        when: "Running a repair and decide on the workflow"
        workflowRepairService.verifyAndRepairWorkflow(workflowInstanceId, true)
        workflowExecutor.decide(workflowInstanceId)
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the next scheduled task can be polled and executed successfully"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }
    }
}
