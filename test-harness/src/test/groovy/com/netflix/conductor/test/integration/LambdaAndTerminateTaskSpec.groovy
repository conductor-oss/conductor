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
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class LambdaAndTerminateTaskSpec extends AbstractSpecification {

    @Shared
    def WORKFLOW_WITH_TERMINATE_TASK = 'test_terminate_task_wf'

    @Shared
    def WORKFLOW_WITH_TERMINATE_TASK_FAILED = 'test_terminate_task_failed_wf'

    @Shared
    def WORKFLOW_WITH_LAMBDA_TASK = 'test_lambda_wf'

    @Shared
    def PARENT_WORKFLOW_WITH_TERMINATE_TASK = 'test_terminate_task_parent_wf'

    @Shared
    def WORKFLOW_WITH_DECISION_AND_TERMINATE = "ConditionalTerminateWorkflow"

    @Autowired
    SubWorkflow subWorkflowTask

    def setup() {
        workflowTestUtil.registerWorkflows(
                'failure_workflow_for_terminate_task_workflow.json',
                'terminate_task_completed_workflow_integration_test.json',
                'terminate_task_failed_workflow_integration.json',
                'simple_lambda_workflow_integration_test.json',
                'terminate_task_parent_workflow.json',
                'terminate_task_sub_workflow.json',
                'decision_and_terminate_integration_test.json'
        )
    }

    def "Test workflow with a terminate task when the status is completed"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_TERMINATE_TASK, 1,
                '', workflowInput, null, null, null)

        then: "Ensure that the workflow has started and the first task is in scheduled state and workflow output should be terminate task's output"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            reasonForIncompletion.contains('Workflow is COMPLETED by TERMINATE task')
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'TERMINATE'
            tasks[1].seq == 2
            output.size() == 1
            output as String == "[result:[testvalue:true]]"
        }
    }

    def "Test workflow with a terminate task when the status is failed"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_TERMINATE_TASK_FAILED, 1,
                '', workflowInput, null, null, null)

        then: "Verify that the workflow has failed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            reasonForIncompletion == "Early exit in terminate"
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'TERMINATE'
            tasks[1].seq == 2
            output
            def failedWorkflowId = output['conductor.failure_workflow'] as String
            with(workflowExecutionService.getExecutionStatus(failedWorkflowId, true)) {
                status == Workflow.WorkflowStatus.COMPLETED
                input['workflowId'] == workflowInstanceId
                tasks.size() == 1
                tasks[0].taskType == 'LAMBDA'
            }
        }
    }

    def "Test workflow with a terminate task when the workflow has a subworkflow"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(PARENT_WORKFLOW_WITH_TERMINATE_TASK, 1,
                '', workflowInput, null, null, null)

        then: "verify that the workflow has started and the tasks are as expected"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'LAMBDA'
            tasks[1].referenceTaskName == 'lambdaTask1'
            tasks[1].seq == 2
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'LAMBDA'
            tasks[2].referenceTaskName == 'lambdaTask2'
            tasks[2].seq == 3
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[3].seq == 4
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].seq == 5
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[5].taskType == 'WAIT'
            tasks[5].seq == 6
        }

        when: "subworkflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId = workflow.getTaskByRefName("test_terminate_subworkflow").getTaskId()
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.getTaskByRefName("test_terminate_subworkflow").subWorkflowId

        then: "verify that the sub workflow is RUNNING, and the task within is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_3'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Complete the WAIT task that should cause the TERMINATE task to execute"
        def waitTask = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[5]
        waitTask.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(waitTask))

        then: "Verify that the workflow has completed and the SUB_WORKFLOW is not still IN_PROGRESS (should be SKIPPED)"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 7
            reasonForIncompletion.contains('Workflow is COMPLETED by TERMINATE task')
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'LAMBDA'
            tasks[1].referenceTaskName == 'lambdaTask1'
            tasks[1].seq == 2
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'LAMBDA'
            tasks[2].referenceTaskName == 'lambdaTask2'
            tasks[2].seq == 3
            tasks[3].status == Task.Status.CANCELED
            tasks[3].taskType == 'JOIN'
            tasks[3].seq == 4
            tasks[4].status == Task.Status.CANCELED
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].seq == 5
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].taskType == 'WAIT'
            tasks[5].seq == 6
            tasks[6].status == Task.Status.COMPLETED
            tasks[6].taskType == 'TERMINATE'
            tasks[6].seq == 7
        }

        and: "ensure that the subworkflow is terminated"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            reasonForIncompletion.contains('Parent workflow has been terminated with reason: Workflow is COMPLETED by' +
                    ' TERMINATE task')
            tasks[0].taskType == 'integration_task_3'
            tasks[0].status == Task.Status.CANCELED
        }
    }

    def "Test workflow with a terminate task within a decision branch"() {
        given: "workflow input"
        Map workflowInput = new HashMap<String, Object>()
        workflowInput['param1'] = 'p1'
        workflowInput['param2'] = 'p2'
        workflowInput['case'] = 'two'

        when: "The workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_DECISION_AND_TERMINATE, 1, '',
                workflowInput, null, null, null)

        then: "verify that the workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].seq == 1
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op':'task1 completed'])

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has FAILED due to terminate task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 3
            output.size() == 1
            output as String == "[output:task1 completed]"
            reasonForIncompletion.contains('Workflow is FAILED by TERMINATE task')
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['op'] == 'task1 completed'
            tasks[0].seq == 1
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].seq == 2
            tasks[2].taskType == 'TERMINATE'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].seq == 3
        }
    }

    def "Test workflow with lambda task"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_LAMBDA_TASK, 1,
                '', workflowInput, null, null, null)

        then: "verify that the task is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].outputData as String == "[result:[testvalue:true]]"
            tasks[0].seq == 1
        }
    }
}
