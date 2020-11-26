/*
 * Copyright 2020 Netflix, Inc.
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

class JsonJQTransformSpec extends AbstractSpecification {

    @Shared
    def JSON_JQ_TRANSFORM_WF = 'test_json_jq_transform_wf'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'simple_json_jq_transform_integration_test.json',
        )
    }

    /**
     * Given the following input JSON
     *{*   "in1": {*     "array": [ "a", "b" ]
     *},
     *   "in2": {*     "array": [ "c", "d" ]
     *}*}* expect the workflow task to transform to following result:
     *{*     out: [ "a", "b", "c", "d" ]
     *}*/
    def "Test workflow with json jq transform task succeeds"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['in1'] = new HashMap()
        workflowInput['in1']['array'] = ["a", "b"]
        workflowInput['in2'] = new HashMap()
        workflowInput['in2']['array'] = ["c", "d"]

        when: "workflow which has the json jq transform task has started"
        def workflowInstanceId = workflowExecutor.startWorkflow(JSON_JQ_TRANSFORM_WF, 1,
                '', workflowInput, null, null, null)

        then: "verify that the workflow and task are completed with expected output"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'JSON_JQ_TRANSFORM'
            tasks[0].outputData as String == "[result:[out:[a, b, c, d]], resultList:[[out:[a, b, c, d]]]]"
        }
    }

    /**
     * Given the following input JSON
     *{*   "in1": "a",
     *   "in2": "b"
     *}* using the same query from the success test, jq will try to get in1['array']
     * and fail since 'in1' is a string
     */
    def "Test workflow with json jq transform task fails"() {
        given: "workflow input"
        def workflowInput = new HashMap()
        workflowInput['in1'] = "a"
        workflowInput['in2'] = "b"

        when: "workflow which has the json jq transform task has started"
        def workflowInstanceId = workflowExecutor.startWorkflow(JSON_JQ_TRANSFORM_WF, 1,
                '', workflowInput, null, null, null)

        then: "verify that the workflow and task failed with expected error"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'JSON_JQ_TRANSFORM'
            tasks[0].reasonForIncompletion as String == "Cannot index string with string \"array\""
        }
    }
}
