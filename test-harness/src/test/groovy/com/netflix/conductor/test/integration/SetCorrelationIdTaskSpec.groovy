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
package com.netflix.conductor.test.integration

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class SetCorrelationIdTaskSpec extends AbstractSpecification {

    @Shared
    def SET_CORRELATION_ID_WF = 'test_set_correlation_id_wf'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'simple_set_correlation_id_workflow_integration_test.json'
        )
    }

    def "Test workflow with set correlation id task"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['correlationId'] = "my-unique-correlation-id"

        when: "Start the workflow which has the set correlation id task"
        def workflowInstanceId = startWorkflow(SET_CORRELATION_ID_WF, 1,
                '', workflowInput, null)

        then: "verify that the task is completed and variables were set"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'SET_CORRELATION_ID'
            tasks[0].status == Task.Status.COMPLETED
            output as String == '[correlationId:my-unique-correlation-id]'
        }
    }

    def "Test workflow with missing correlation id"() {
        given: "workflow input"
        def workflowInput = new HashMap()

        when: "Start the workflow which has the set variable task"
        def workflowInstanceId = startWorkflow(SET_CORRELATION_ID_WF, 1,
                '', workflowInput, null)
                
        def expectedErrorMessage = "A non-empty String value must be provided for 'correlationId'"

        then: "verify that the task failed with a message"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].taskType == 'SET_CORRELATION_ID'
            tasks[0].status == Task.Status.FAILED_WITH_TERMINAL_ERROR
            tasks[0].reasonForIncompletion == expectedErrorMessage
        }
    }
}
