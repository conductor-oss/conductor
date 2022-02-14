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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class FailureWorkflowSpec extends AbstractSpecification {

    @Shared
    def WORKFLOW_WITH_TERMINATE_TASK_FAILED = 'test_terminate_task_failed_wf'

    @Shared
    def PARENT_WORKFLOW_WITH_FAILURE_TASK = 'test_task_failed_parent_wf'

    @Autowired
    SubWorkflow subWorkflowTask

    def setup() {
        workflowTestUtil.registerWorkflows(
                'failure_workflow_for_terminate_task_workflow.json',
                'terminate_task_failed_workflow_integration.json',
                'test_task_failed_parent_workflow.json',
                'test_task_failed_sub_workflow.json'
        )
    }

    def "Test workflow with a task that failed"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the failed task"
        def testId = 'testId'
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_TERMINATE_TASK_FAILED, 1,
                testId, workflowInput, null, null, null)

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
            def workflowCorrelationId = correlationId
            def workflowFailureTaskId = tasks[1].taskId
            with(workflowExecutionService.getExecutionStatus(failedWorkflowId, true)) {
                status == Workflow.WorkflowStatus.COMPLETED
                correlationId == workflowCorrelationId
                input['workflowId'] == workflowInstanceId
                input['failureTaskId'] == workflowFailureTaskId
                tasks.size() == 1
                tasks[0].taskType == 'LAMBDA'
            }
        }
    }

    def "Test workflow with a task failed in subworkflow"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the subworkflow task"
        def workflowInstanceId = workflowExecutor.startWorkflow(PARENT_WORKFLOW_WITH_FAILURE_TASK, 1,
                '', workflowInput, null, null, null)

        then: "verify that the workflow has started and the tasks are as expected"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].referenceTaskName == 'lambdaTask1'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].seq == 2
        }

        when: "subworkflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId = workflow.getTaskByRefName("test_task_failed_sub_wf").getTaskId()
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.getTaskByRefName("test_task_failed_sub_wf").subWorkflowId

        then: "verify that the sub workflow has failed"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            reasonForIncompletion.contains('Workflow is FAILED by TERMINATE task')
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'TERMINATE'
            tasks[1].seq == 2
        }

        then: "Verify that the workflow has failed and correct inputs passed into the failure workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].referenceTaskName == 'lambdaTask1'
            tasks[0].seq == 1
            tasks[1].status == Task.Status.FAILED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].seq == 2
            def failedWorkflowId = output['conductor.failure_workflow'] as String
            def workflowCorrelationId = correlationId
            def workflowFailureTaskId = tasks[1].taskId
            with(workflowExecutionService.getExecutionStatus(failedWorkflowId, true)) {
                status == Workflow.WorkflowStatus.COMPLETED
                correlationId == workflowCorrelationId
                input['workflowId'] == workflowInstanceId
                input['failureTaskId'] == workflowFailureTaskId
                tasks.size() == 1
                tasks[0].taskType == 'LAMBDA'
            }
        }
    }
}
