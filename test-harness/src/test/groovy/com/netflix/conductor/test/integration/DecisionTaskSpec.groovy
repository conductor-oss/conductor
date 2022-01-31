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
import spock.lang.Unroll

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class DecisionTaskSpec extends AbstractSpecification {

    @Shared
    def DECISION_WF = "DecisionWorkflow"

    @Shared
    def FORK_JOIN_DECISION_WF = "ForkConditionalTest"

    @Shared
    def COND_TASK_WF = "ConditionalTaskWF"

    def setup() {
        //initialization code for each feature
        workflowTestUtil.registerWorkflows('simple_decision_task_integration_test.json',
                'decision_and_fork_join_integration_test.json',
                'conditional_task_workflow_integration_test.json')
    }

    def "Test simple decision workflow"() {
        given: "Workflow an input of a workflow with decision task"
        Map input = new HashMap<String, Object>()
        input['param1'] = 'p1'
        input['param2'] = 'p2'
        input['case'] = 'c'

        when: "A decision workflow is started with the workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(DECISION_WF, 1,
                'decision_workflow', input,
                null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DECISION'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_1' is polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'DECISION'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_2' is polled and completed"
        def polledAndCompletedTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker')

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1)

        and: "verify that the 'integration_task_2' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_20'
            tasks[3].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_20' is polled and completed"
        def polledAndCompletedTask20Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_20', 'task1.integration.worker')

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask20Try1)

        and: "verify that the 'integration_task_20' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[3].taskType == 'integration_task_20'
            tasks[3].status == Task.Status.COMPLETED
        }
    }

    def "Test a workflow that has a decision task that leads to a fork join"() {
        given: "Workflow an input of a workflow with decision task"
        Map input = new HashMap<String, Object>()
        input['param1'] = 'p1'
        input['param2'] = 'p2'
        input['case'] = 'c'

        when: "A decision workflow is started with the workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_DECISION_WF, 1,
                'decision_forkjoin', input,
                null, null, null)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
        }

        when: "the tasks 'integration_task_1' and 'integration_task_10' are polled and completed"
        def polledAndCompletedTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')
        def polledAndCompletedTask10Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_10', 'task1.integration.worker')

        then: "verify that the tasks are completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1)
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask10Try1)

        and: "verify that the 'integration_task_1' and 'integration_task_10' are COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[2].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['t20', 't10']
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_2' is polled and completed"
        def polledAndCompletedTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker')

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1)

        and: "verify that the 'integration_task_2' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['t20', 't10']
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_20'
            tasks[6].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_20' is polled and completed"
        def polledAndCompletedTask20Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_20', 'task1.integration.worker')

        then: "verify that the task is completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask20Try1)

        and: "verify that the 'integration_task_2' is COMPLETED and the workflow has progressed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 7
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['t20', 't10']
            tasks[4].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_20'
            tasks[6].status == Task.Status.COMPLETED
        }
    }

    def "Test default case condition execution of a conditional workflow"() {
        given: "input for a workflow to ensure that the default case is executed"
        Map input = new HashMap<String, Object>()
        input['param1'] = 'xxx'
        input['param2'] = 'two'

        when: "A conditional workflow is started with the workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(COND_TASK_WF, 1,
                'conditional_default', input,
                null, null, null)

        then: "verify that the workflow is running and the default condition case was executed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DECISION'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['caseOutput'] == ['xxx']
            tasks[1].taskType == 'integration_task_10'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_10' is polled and completed"
        def polledAndCompletedTask10Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_10', 'task1.integration.worker')

        then: "verify that the tasks are completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask10Try1)

        and: "verify that the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_10'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'DECISION'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].outputData['caseOutput'] == ['null']
        }
    }

    @Unroll
    def "Test case 'nested' and '#caseValue' condition execution of a conditional workflow"() {
        given: "input for a workflow to ensure that the 'nested' and '#caseValue' decision tree is executed"
        Map input = new HashMap<String, Object>()
        input['param1'] = 'nested'
        input['param2'] = caseValue

        when: "A conditional workflow is started with the workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(COND_TASK_WF, 1,
                workflowCorrelationId, input,
                null, null, null)

        then: "verify that the workflow is running and the 'nested' and '#caseValue' condition case was executed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'DECISION'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['caseOutput'] == ['nested']
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].outputData['caseOutput'] == [caseValue]
            tasks[2].taskType == expectedTaskName
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the task '#expectedTaskName' is polled and completed"
        def polledAndCompletedTaskTry1 = workflowTestUtil.pollAndCompleteTask(expectedTaskName, 'task.integration.worker')

        then: "verify that the tasks are completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTaskTry1)

        and:
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[2].taskType == expectedTaskName
            tasks[2].status == endTaskStatus
            tasks[3].taskType == 'DECISION'
            tasks[3].status == Task.Status.COMPLETED
            tasks[3].outputData['caseOutput'] == ['null']
        }

        where:
        caseValue | expectedTaskName     | workflowCorrelationId    || endTaskStatus
        'two'     | 'integration_task_2' | 'conditional_nested_two' || Task.Status.COMPLETED
        'one'     | 'integration_task_1' | 'conditional_nested_one' || Task.Status.COMPLETED
    }

    def "Test 'three' case condition execution of a conditional workflow"() {
        given: "input for a workflow to ensure that the default case is executed"
        Map input = new HashMap<String, Object>()
        input['param1'] = 'three'
        input['param2'] = 'two'
        input['finalCase'] = 'notify'

        when: "A conditional workflow is started with the workflow input"
        def workflowInstanceId = workflowExecutor.startWorkflow(COND_TASK_WF, 1,
                'conditional_three', input,
                null, null, null)

        then: "verify that the workflow is running and the 'three' condition case was executed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DECISION'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['caseOutput'] == ['three']
            tasks[1].taskType == 'integration_task_3'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_3' is polled and completed"
        def polledAndCompletedTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task1.integration.worker')

        then: "verify that the tasks are completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask3Try1)

        and: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[1].taskType == 'integration_task_3'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'DECISION'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].outputData['caseOutput'] == ['notify']
            tasks[3].taskType == 'integration_task_4'
            tasks[3].status == Task.Status.SCHEDULED
        }

        when: "the task 'integration_task_4' is polled and completed"
        def polledAndCompletedTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task1.integration.worker')

        then: "verify that the tasks are completed and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4Try1)

        and: "verify that the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[1].taskType == 'integration_task_3'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'DECISION'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].outputData['caseOutput'] == ['notify']
            tasks[3].taskType == 'integration_task_4'
            tasks[3].status == Task.Status.COMPLETED
        }
    }
}
