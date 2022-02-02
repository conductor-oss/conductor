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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class SetVariableTaskSpec extends AbstractSpecification {

    @Shared
    def SET_VARIABLE_WF = 'test_set_variable_wf'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'simple_set_variable_workflow_integration_test.json'
        )
    }

    def "Test workflow with set variable task"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['var'] = "var_test_value"

        when: "Start the workflow which has the set variable task"
        def workflowInstanceId = workflowExecutor.startWorkflow(SET_VARIABLE_WF, 1,
                '', workflowInput, null, null, null)

        then: "verify that the task is completed and variables were set"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'SET_VARIABLE'
            tasks[0].status == Task.Status.COMPLETED
            variables as String == '[var:var_test_value]'
            output as String == '[variables:[var:var_test_value]]'
        }
    }

    def "Test workflow with set variable task passing variables payload size threshold"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        long maxThreshold = 2
        workflowInput['var'] = String.join("",
                Collections.nCopies(1 + ((int) (maxThreshold * 1024 / 8)), "01234567"))

        when: "Start the workflow which has the set variable task"
        def workflowInstanceId = workflowExecutor.startWorkflow(SET_VARIABLE_WF, 1,
                '', workflowInput, null, null, null)
        def EXTRA_HASHMAP_SIZE = 17
        def expectedErrorMessage =
                String.format(
                        "The variables payload size: %d of workflow: %s is greater than the permissible limit: %d bytes",
                        EXTRA_HASHMAP_SIZE + maxThreshold * 1024 + 1, workflowInstanceId, maxThreshold)

        then: "verify that the task is completed and variables were set"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].taskType == 'SET_VARIABLE'
            tasks[0].status == Task.Status.FAILED_WITH_TERMINAL_ERROR
            tasks[0].reasonForIncompletion == expectedErrorMessage
            variables as String == '[:]'
            output as String == '[variables:[:]]'
        }
    }
}
