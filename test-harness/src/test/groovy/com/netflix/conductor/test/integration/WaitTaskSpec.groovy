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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedLargePayloadTask
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class WaitTaskSpec extends AbstractSpecification {

    @Shared
    def WAIT_BASED_WORKFLOW = 'test_wait_workflow'
    def SET_VARIABLE_WORKFLOW = 'set_variable_workflow_integration_test'

    def setup() {
        workflowTestUtil.registerWorkflows('wait_workflow_integration_test.json',
                'set_variable_workflow_integration_test.json')
    }

    def "Test workflow with set variable task"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['var'] = "var_test_value"

        when: "Start the workflow which has the set variable task"
        def workflowInstanceId = startWorkflow(SET_VARIABLE_WORKFLOW, 1,
                '', workflowInput, null)

        then: "verify that the simple task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'simple'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'simple' with external payload storage"
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteTask('simple', 'simple.worker',
                ['ok1': 'ov1'])

        then: "verify that the 'simple' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        then: "ensure that the wait task is completed and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'simple'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SET_VARIABLE'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'WAIT'
            tasks[2].status == Task.Status.IN_PROGRESS
            variables as String == '[var:var_test_value]'
        }

        when: "The wait task is completed"
        def waitTask = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[2]
        waitTask.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(waitTask))

        then: "ensure that the wait task is completed and the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'simple'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SET_VARIABLE'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'WAIT'
            tasks[2].status == Task.Status.COMPLETED
            variables as String == '[var:var_test_value]'
            output as String == '[variables:[var:var_test_value]]'
        }
    }

    def "Verify that a wait based simple workflow is executed"() {
        when: "Start a wait task based workflow"
        def workflowInstanceId = startWorkflow(WAIT_BASED_WORKFLOW, 1,
                '', [:], null)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == TaskType.WAIT.name()
            tasks[0].status == Task.Status.IN_PROGRESS
        }

        when: "The wait task is completed"
        def waitTask = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[0]
        waitTask.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(waitTask))

        then: "ensure that the wait task is completed and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == TaskType.WAIT.name()
            tasks[0].status == Task.Status.COMPLETED
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
}
