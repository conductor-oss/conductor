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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class ExclusiveJoinSpec extends AbstractSpecification {

    @Shared
    def EXCLUSIVE_JOIN_WF = "ExclusiveJoinTestWorkflow"

    def setup() {
        workflowTestUtil.registerWorkflows('exclusive_join_integration_test.json')
    }

    def setTaskResult(String workflowInstanceId, String taskId, TaskResult.Status status,
                      Map<String, Object> output) {
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(taskId)
        taskResult.setWorkflowInstanceId(workflowInstanceId)
        taskResult.setStatus(status)
        taskResult.setOutputData(output)
        return taskResult
    }

    def "Test that the default decision is run"() {
        given: "The input parameter required to make decision_1 is null to ensure that the default decision is run"
        def input = ["decision_1": "null"]

        when: "An exclusive join workflow is started with then workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(EXCLUSIVE_JOIN_WF, 1, 'exclusive_join_workflow',
                input, null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1' +
                '.integration.worker', ["taskReferenceName": "task1"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'EXCLUSIVE_JOIN'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].outputData['taskReferenceName'] == 'task1'
        }
    }

    def "Test when the one decision is true and the other is decision null"() {
        given: "The input parameter required to make decision_1 true and decision_2 null"
        def input = ["decision_1": "true", "decision_2": "null"]

        when: "An exclusive join workflow is started with then workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(EXCLUSIVE_JOIN_WF, 1, 'exclusive_join_workflow',
                input, null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1' +
                '.integration.worker', ["taskReferenceName": "task1"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_2' is polled and completed"
        def polledAndCompletedTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2' +
                '.integration.worker', ["taskReferenceName": "task2"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1)

        and: "verify that the 'integration_task_2' is COMPLETED and the workflow has COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'EXCLUSIVE_JOIN'
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].outputData['taskReferenceName'] == 'task2'
        }
    }

    def "Test when both the decisions, decision_1 and decision_2 are true"() {
        given: "The input parameters to ensure that both the decisions are true"
        def input = ["decision_1": "true", "decision_2": "true"]

        when: "An exclusive join workflow is started with then workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(EXCLUSIVE_JOIN_WF, 1, 'exclusive_join_workflow',
                input, null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1' +
                '.integration.worker', ["taskReferenceName": "task1"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_2' is polled and completed"
        def polledAndCompletedTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2' +
                '.integration.worker', ["taskReferenceName": "task2"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1)

        and: "verify that the 'integration_task_2' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_3'
            tasks[4].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_3' is polled and completed"
        def polledAndCompletedTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3' +
                '.integration.worker', ["taskReferenceName": "task3"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask3Try1)

        and: "verify that the 'integration_task_3' is COMPLETED and the workflow has COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_3'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'EXCLUSIVE_JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].outputData['taskReferenceName'] == 'task3'
        }
    }

    def "Test when decision_1 is false and  decision_3 is default"() {
        given: "The input parameter required to make decision_1 false and decision_3 default"
        def input = ["decision_1": "false", "decision_3": "null"]

        when: "An exclusive join workflow is started with then workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(EXCLUSIVE_JOIN_WF, 1, 'exclusive_join_workflow',
                input, null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1' +
                '.integration.worker', ["taskReferenceName": "task1"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_4'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_4' is polled and completed"
        def polledAndCompletedTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4' +
                '.integration.worker', ["taskReferenceName": "task4"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4Try1)

        and: "verify that the 'integration_task_4' is COMPLETED and the workflow has COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_4'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'EXCLUSIVE_JOIN'
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].outputData['taskReferenceName'] == 'task4'
        }
    }

    def "Test when decision_1 is false and  decision_3 is true"() {
        given: "The input parameter required to make decision_1 false and decision_3 true"
        def input = ["decision_1": "false", "decision_3": "true"]

        when: "An exclusive join workflow is started with then workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(EXCLUSIVE_JOIN_WF, 1, 'exclusive_join_workflow',
                input, null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1' +
                '.integration.worker', ["taskReferenceName": "task1"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_4'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_4' is polled and completed"
        def polledAndCompletedTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4' +
                '.integration.worker', ["taskReferenceName": "task4"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4Try1)

        and: "verify that the 'integration_task_4' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_4'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_5'
            tasks[4].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_5' is polled and completed"
        def polledAndCompletedTask5Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_5', 'task5' +
                '.integration.worker', ["taskReferenceName": "task5"])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask5Try1)

        and: "verify that the 'integration_task_4' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_4'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_5'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'EXCLUSIVE_JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].outputData['taskReferenceName'] == 'task5'
        }
    }
}
