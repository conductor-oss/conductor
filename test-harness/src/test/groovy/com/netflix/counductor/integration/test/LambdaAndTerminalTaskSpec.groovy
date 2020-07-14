/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.counductor.integration.test

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@ModulesForTesting([TestModule.class])
class LambdaAndTerminalTaskSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowTestUtil workflowTestUtil

    @Shared
    def WORKFLOW_WITH_TERMINATE_TASK = 'test_terminate_task_wf'

    @Shared
    def WORKFLOW_WITH_TERMINATE_TASK_FAILED = 'test_terminate_task_failed_wf'

    @Shared
    def  WORKFLOW_WITH_LAMBDA_TASK = 'test_lambda_wf'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'failure_workflow_for_terminate_task_workflow.json',
                'terminate_task_completed_workflow_integration_test.json',
                'terminate_task_failed_workflow_integration.json',
                'simple_lambda_workflow_integration_test.json'
        )
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
    }

    def "Test workflow with a terminate task when the status is completed"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_TERMINATE_TASK, 1,
                '', workflowInput, null, null, null)

        then: "Ensure that the workflow has started and the first task is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'TERMINATE'
        }
    }

    def "Test workflow with a terminate task when the status is failed"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_TERMINATE_TASK_FAILED, 1,
                '', workflowInput, null, null, null)

        then:"Verify that the workflow has failed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'TERMINATE'
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

    def "Test workflow with lambda task"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['a'] = 1

        when: "Start the workflow which has the terminate task"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_LAMBDA_TASK, 1,
                '', workflowInput, null, null, null)

        then:"verify that the task is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'LAMBDA'
            tasks[0].outputData as String == "[result:[testvalue:true]]"
        }
    }

}
