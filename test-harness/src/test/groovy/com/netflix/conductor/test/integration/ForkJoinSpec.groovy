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
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class ForkJoinSpec extends AbstractSpecification {

    @Shared
    def FORK_JOIN_WF = 'FanInOutTest'

    @Shared
    def FORK_JOIN_NESTED_WF = 'FanInOutNestedTest'

    @Shared
    def FORK_JOIN_NESTED_SUB_WF = 'FanInOutNestedSubWorkflowTest'

    @Shared
    def WORKFLOW_FORK_JOIN_OPTIONAL_SW = "integration_test_fork_join_optional_sw"

    @Shared
    def FORK_JOIN_SUB_WORKFLOW = 'integration_test_fork_join_sw'

    @Autowired
    SubWorkflow subWorkflowTask

    def setup() {
        workflowTestUtil.registerWorkflows('fork_join_integration_test.json',
                'fork_join_with_no_task_retry_integration_test.json',
                'nested_fork_join_integration_test.json',
                'simple_workflow_1_integration_test.json',
                'nested_fork_join_with_sub_workflow_integration_test.json',
                'simple_one_task_sub_workflow_integration_test.json',
                'fork_join_with_optional_sub_workflow_forks_integration_test.json',
                'fork_join_sub_workflow.json'
        )
    }

    /**
     *             start
     *              |
     *             fork
     *            /     \
     *         task1     task2
     *          |        /
     *         task3    /
     *           \     /
     *            \  /
     *            join
     *              |
     *            task4
     *              |
     *             End
     */
    def "Test a simple workflow with fork join success flow"() {
        when: "A fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_WF, 1,
                'fanoutTest', [:],
                null, null, null)

        then: "verify that the workflow has started and the starting nodes of the each fork are in scheduled state"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
        }

        when: "The first task of the fork is polled and completed"
        def polledAndAckTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker')

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1)

        and: "The workflow has been updated and has all the required tasks in the right status to move forward"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_3'
        }

        when: "The 'integration_task_3' is polled and completed"
        def polledAndAckTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task1.worker')

        then: "verify that the 'integration_task_3' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask3Try1)

        and: "The workflow has been updated with the task status and task list"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_3'
        }

        when: "The other node of the fork is completed by completing 'integration_task_2'"
        def polledAndAckTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.worker')

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1)

        and: "The workflow has been updated with the task status and task list"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[3].taskType == 'JOIN'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'integration_task_4'
        }

        when: "The last task of the workflow is then polled and completed integration_task_4'"
        def polledAndAckTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task1.worker')

        then: "verify that the 'integration_task_4' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask4Try1)

        and: "Then verify that the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].taskType == 'integration_task_4'
        }
    }

    def "Test a simple workflow with fork join failure flow"() {
        setup: "Ensure that 'integration_task_2' has a retry count of 0"
        def persistedIntegrationTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedIntegrationTask2Definition = new TaskDef(persistedIntegrationTask2Definition.name,
                persistedIntegrationTask2Definition.description, persistedIntegrationTask2Definition.ownerEmail, 0,
                0, persistedIntegrationTask2Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedIntegrationTask2Definition)

        when: "A fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_WF, 1,
                'fanoutTest', [:],
                null, null, null)

        then: "verify that the workflow has started and the starting nodes of the each fork are in scheduled state"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
        }

        when: "The first task of the fork is polled and completed"
        def polledAndAckTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker')

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1)

        and: "The workflow has been updated and has all the required tasks in the right status to move forward"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_3'
        }

        when: "The other node of the fork is completed by completing 'integration_task_2'"
        def polledAndAckTask2Try1 = workflowTestUtil.pollAndFailTask('integration_task_2',
                'task1.worker', 'Failed....')

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1)

        and: "the workflow is in the failed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 5
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.CANCELED
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].taskType == 'integration_task_3'
        }

        cleanup: "Restore the task definitions that were modified as part of this feature testing"
        metadataService.updateTaskDef(persistedIntegrationTask2Definition)
    }

    def "Test retrying a failed fork join workflow"() {

        when: "A fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_WF + '_2', 1,
                'fanoutTest', [:],
                null, null, null)

        then: "verify that the workflow has started and the starting nodes of the each fork are in scheduled state"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'integration_task_0_RT_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_0_RT_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
        }

        when: "The first task of the fork is polled and completed"
        def polledAndAckTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_0_RT_1', 'task1.worker')

        then: "verify that the 'integration_task_0_RT_1' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1)

        and: "The workflow has been updated and has all the required tasks in the right status to move forward"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0_RT_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_0_RT_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_0_RT_3'
        }

        when: "The other node of the fork is completed by completing 'integration_task_0_RT_2'"
        def polledAndAckTask2Try1 = workflowTestUtil.pollAndFailTask('integration_task_0_RT_2',
                'task1.worker', 'Failed....')

        then: "verify that the 'integration_task_0_RT_1' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1)

        and: "the workflow is in the failed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 5
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0_RT_1'
            tasks[2].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0_RT_2'
            tasks[3].status == Task.Status.CANCELED
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].taskType == 'integration_task_0_RT_3'
        }

        when: "The workflow is retried"
        workflowExecutor.retry(workflowInstanceId, false)

        then: "verify that all the workflow is retried and new tasks are added in place of the failed tasks"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0_RT_1'
            tasks[2].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0_RT_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].taskType == 'integration_task_0_RT_3'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'integration_task_0_RT_2'
            tasks[6].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'integration_task_0_RT_3'
        }

        when: "The 'integration_task_0_RT_3' is polled and completed"
        def polledAndAckTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_0_RT_3', 'task1.worker')

        then: "verify that the 'integration_task_3' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask3Try1)


        when: "The other node of the fork is completed by completing 'integration_task_0_RT_2'"
        def polledAndAckTask2Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_0_RT_2', 'task1.worker')

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask2Try2)

        when: "The last task of the workflow is then polled and completed integration_task_0_RT_4'"
        def polledAndAckTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_0_RT_4', 'task1.worker')

        then: "verify that the 'integration_task_0_RT_4' was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask4Try1)

        and: "Then verify that the workflow is completed and the task list of execution is as expected"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 8
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0_RT_1'
            tasks[2].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0_RT_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].taskType == 'integration_task_0_RT_3'
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].taskType == 'integration_task_0_RT_2'
            tasks[6].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0_RT_3'
            tasks[7].status == Task.Status.COMPLETED
            tasks[7].taskType == 'integration_task_0_RT_4'
        }
    }

    def "Test nested fork join workflow success flow"() {
        given: "Input for the nested fork join workflow"
        Map input = new HashMap<String, Object>()
        input["case"] = "a"

        when: "A nested workflow is started with the input"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_NESTED_WF, 1,
                'fork_join_nested_test', input,
                null, null, null)

        then: "verify that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks.findAll { it.referenceTaskName in ['t11', 't12', 't13', 'fork1', 'fork2'] }.size() == 5
            tasks.findAll { it.referenceTaskName in ['t1', 't2', 't16'] }.size() == 0
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_11'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_12'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_13'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[5].inputData['joinOn'] == ['t11', 'join2']
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t14', 't20']
        }

        when: "Poll and Complete tasks: 'integration_task_11', 'integration_task_12' and 'integration_task_13'"
        def polledAndAckTask11Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_11', 'task11.worker')
        def polledAndAckTask12Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_12', 'task12.worker')
        def polledAndAckTask13Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_13', 'task13.worker')


        then: "verify that tasks 'integration_task_11', 'integration_task_12' and 'integration_task_13' were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask11Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask12Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask13Try1)

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 10
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_11'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_12'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_13'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[5].inputData['joinOn'] == ['t11', 'join2']
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t14', 't20']
            tasks[7].taskType == 'integration_task_14'
            tasks[7].status == Task.Status.SCHEDULED
            tasks[8].taskType == 'DECISION'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'integration_task_16'
            tasks[9].status == Task.Status.SCHEDULED
        }

        when: "Poll and Complete tasks: 'integration_task_16' and 'integration_task_14'"
        def polledAndAckTask16Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_16', 'task16.worker')
        def polledAndAckTask14Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_14', 'task14.worker')

        then: "verify that tasks 'integration_task_16' and 'integration_task_14'were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask16Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask14Try1)

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 11
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[5].inputData['joinOn'] == ['t11', 'join2']
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t14', 't20']
            tasks[7].taskType == 'integration_task_14'
            tasks[7].status == Task.Status.COMPLETED
            tasks[8].taskType == 'DECISION'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'integration_task_16'
            tasks[9].status == Task.Status.COMPLETED
            tasks[10].taskType == 'integration_task_19'
            tasks[10].status == Task.Status.SCHEDULED
        }

        when: "Poll and Complete tasks: 'integration_task_19'"
        def polledAndAckTask19Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_19', 'task19.worker')

        then: "verify that tasks 'integration_task_19' polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask19Try1)

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 12
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[5].inputData['joinOn'] == ['t11', 'join2']
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t14', 't20']
            tasks[10].taskType == 'integration_task_19'
            tasks[10].status == Task.Status.COMPLETED
            tasks[11].taskType == 'integration_task_20'
            tasks[11].status == Task.Status.SCHEDULED
        }

        when: "Poll and Complete tasks: 'integration_task_20'"
        def polledAndAckTask20Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_20', 'task20.worker')

        then: "verify that tasks 'integration_task_20'polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask20Try1)

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 13
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[5].inputData['joinOn'] == ['t11', 'join2']
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.COMPLETED
            tasks[6].inputData['joinOn'] == ['t14', 't20']
            tasks[11].taskType == 'integration_task_20'
            tasks[11].status == Task.Status.COMPLETED
            tasks[12].taskType == 'integration_task_15'
            tasks[12].status == Task.Status.SCHEDULED
        }

        when: "Poll and Complete tasks: 'integration_task_15'"
        def polledAndAckTask15Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_15', 'task15.worker')

        then: "verify that tasks 'integration_task_15' polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask15Try1)

        and: "verify that the workflow is in a complete state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 13
            tasks[12].taskType == 'integration_task_15'
            tasks[12].status == Task.Status.COMPLETED
        }
    }

    def "Test nested workflow which contains a sub workflow task"() {
        given: "Input for the nested fork join workflow"
        Map input = new HashMap<String, Object>()
        input["case"] = "a"

        when: "A nested workflow is started with the input"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_NESTED_SUB_WF, 1,
                'fork_join_nested_test', input,
                null, null, null)

        then: "The workflow is in the running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 8
            tasks.findAll { it.referenceTaskName in ['t11', 't12', 't13', 'fork1', 'fork2', 'sw1'] }.size() == 6
            tasks.findAll { it.referenceTaskName in ['t1', 't2', 't16'] }.size() == 0
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_11'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_12'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_13'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'SUB_WORKFLOW'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].inputData['joinOn'] == ['t14', 't20']
        }

        when: "Poll and Complete tasks: 'integration_task_11', 'integration_task_12' and 'integration_task_13'"
        def polledAndAckTask11Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_11', 'task11.worker')
        def polledAndAckTask12Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_12', 'task12.worker')
        def polledAndAckTask13Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_13', 'task13.worker')
        workflowExecutionService.getExecutionStatus(workflowInstanceId, true)


        then: "verify that tasks 'integration_task_11', 'integration_task_12' and 'integration_task_13' were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask11Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask12Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask13Try1)

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 11
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_11'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_12'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_13'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'SUB_WORKFLOW'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].inputData['joinOn'] == ['t14', 't20']
            tasks[8].taskType == 'integration_task_14'
            tasks[8].status == Task.Status.SCHEDULED
            tasks[9].taskType == 'DECISION'
            tasks[9].status == Task.Status.COMPLETED
            tasks[10].taskType == 'integration_task_16'
            tasks[10].status == Task.Status.SCHEDULED
        }

        when: "Poll and Complete tasks: 'integration_task_16' and 'integration_task_14'"
        def polledAndAckTask16Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_16', 'task16.worker')
        def polledAndAckTask14Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_14', 'task14.worker')

        and: "Get the sub workflow id associated with the SubWorkflow Task sw1 and start the system task"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId()
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)
        def updatedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowInstanceId = updatedWorkflow.getTaskByRefName('sw1').subWorkflowId

        then: "verify that tasks 'integration_task_16' and 'integration_task_14'were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask16Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask14Try1)
        with(updatedWorkflow) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 12
            tasks[5].taskType == 'SUB_WORKFLOW'
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].inputData['joinOn'] == ['t14', 't20']
            tasks[8].taskType == 'integration_task_14'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'DECISION'
            tasks[9].status == Task.Status.COMPLETED
            tasks[10].taskType == 'integration_task_16'
            tasks[10].status == Task.Status.COMPLETED
            tasks[11].taskType == 'integration_task_19'
            tasks[11].status == Task.Status.SCHEDULED
        }

        and: "verify that the simple Sub Workflow is in running state and the first task related to it is scheduled"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete all the tasks associated with the sub workflow"
        def polledAndAckTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker')
        def polledAndAckTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker')

        then: "verify that tasks 'integration_task_1' and 'integration_task_2'were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask1Try1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask2Try1)

        and: "verify that the simple Sub Workflow is in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        and: " verify that the sub workflow task is completed and other preceding tasks are added to the workflow task list"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 12
            tasks[5].taskType == 'SUB_WORKFLOW'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].inputData['joinOn'] == ['t14', 't20']
            tasks[11].taskType == 'integration_task_19'
            tasks[11].status == Task.Status.SCHEDULED
        }

        when: "Also the poll and complete the 'integration_task_19'"
        def polledAndAckTask19Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_19', 'task19.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask19Try1)

        and: "verify that the integration_task_19 is completed and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 13
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].inputData['joinOn'] == ['t14', 't20']
            tasks[11].taskType == 'integration_task_19'
            tasks[11].status == Task.Status.COMPLETED
            tasks[12].taskType == 'integration_task_20'
            tasks[12].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_20'"
        def polledAndAckTask20Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_20', 'task20.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask20Try1)

        and: "verify that the integration_task_20 is completed and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 14
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.COMPLETED
            tasks[6].inputData['joinOn'] == ['t11', 'join2', 'sw1']
            tasks[7].taskType == 'JOIN'
            tasks[7].status == Task.Status.COMPLETED
            tasks[7].inputData['joinOn'] == ['t14', 't20']
            tasks[12].taskType == 'integration_task_20'
            tasks[12].status == Task.Status.COMPLETED
            tasks[13].taskType == 'integration_task_15'
            tasks[13].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_15'"
        def polledAndAckTask15Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_15', 'task15.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckTask15Try1)

        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 14
            tasks[13].taskType == 'integration_task_15'
            tasks[13].status == Task.Status.COMPLETED
        }
    }

    def "Test fork join with sub workflows containing optional tasks"() {
        given: "A input to the workflow that has forks of sub workflows with an optional task"
        Map workflowInput = new HashMap<String, Object>()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "A workflow that has forks of sub workflows with an optional task is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_FORK_JOIN_OPTIONAL_SW, 1,
                '', workflowInput,
                null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].status == Task.Status.IN_PROGRESS
        }

        when: "both the sub workflows are started by issuing a system task call"
        def workflowWithScheduledSubWorkflows = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId1 = workflowWithScheduledSubWorkflows.getTaskByRefName('st1').taskId
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId1)
        def subWorkflowTaskId2 = workflowWithScheduledSubWorkflows.getTaskByRefName('st2').taskId
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId2)

        then: "verify that the sub workflow tasks are in a IN PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 'st2']
            tasks[3].status == Task.Status.IN_PROGRESS
        }

        and: "Also verify that the sub workflows are in a RUNNING state"
        def workflowWithRunningSubWorkflows = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowInstanceId1 = workflowWithRunningSubWorkflows.getTaskByRefName('st1').subWorkflowId
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId1, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        def subWorkflowInstanceId2 = workflowWithRunningSubWorkflows.getTaskByRefName('st2').subWorkflowId
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId2, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        when: "The 'simple_task_in_sub_wf' belonging to both the sub workflows is polled and failed"
        def polledAndAckSubWorkflowTask1 = workflowTestUtil.pollAndFailTask('simple_task_in_sub_wf',
                'task1.worker', 'Failed....')
        def polledAndAckSubWorkflowTask2 = workflowTestUtil.pollAndFailTask('simple_task_in_sub_wf',
                'task1.worker', 'Failed....')

        then: "verify that both the tasks were polled and failed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckSubWorkflowTask1)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndAckSubWorkflowTask2)

        and: "verify that both the sub workflows are in failed state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId1, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId2, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }
        sweep(workflowInstanceId)

        and: "verify that the workflow is in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.COMPLETED_WITH_ERRORS
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.COMPLETED_WITH_ERRORS
            tasks[3].taskType == 'JOIN'
            tasks[3].status == Task.Status.COMPLETED
        }
    }

    def "Test fork join with sub workflow task using task definition"() {
        given: "A input to the workflow that has fork with sub workflow task"
        Map workflowInput = new HashMap<String, Object>()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "A workflow that has fork with sub workflow task is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_SUB_WORKFLOW, 1, '', workflowInput, null,
                null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.IN_PROGRESS
        }

        when: "the subworkflow is started by issuing a system task call"
        def parentWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId = parentWorkflow.getTaskByRefName('st1').taskId
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)

        then: "verify that the sub workflow task is in a IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.IN_PROGRESS
        }

        when: "sub workflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowInstanceId = workflow.getTaskByRefName('st1').subWorkflowId

        then: "verify that the sub workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        when: "the 'simple_task_in_sub_wf' belonging to the sub workflow is polled and failed"
        def polledAndFailSubWorkflowTask = workflowTestUtil.pollAndFailTask('simple_task_in_sub_wf',
                'task1.worker', 'Failed....')

        then: "verify that the task was polled and failed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndFailSubWorkflowTask)

        and: "verify that the sub workflow is in failed state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        and: "verify that the workflow is in a RUNNING state and sub workflow task is retried"
        sweep(workflowInstanceId)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].status == Task.Status.SCHEDULED
        }

        when: "the sub workflow is started by issuing a system task call"
        parentWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        subWorkflowTaskId = parentWorkflow.getTaskByRefName('st1').taskId
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)

        then: "verify that the sub workflow task is in a IN PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].status == Task.Status.IN_PROGRESS
        }

        when: "sub workflow is retrieved"
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        subWorkflowInstanceId = workflow.getTaskByRefName('st1').subWorkflowId

        then: "verify that the sub workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        when: "the 'simple_task_in_sub_wf' belonging to the sub workflow is polled and completed"
        def polledAndCompletedSubWorkflowTask = workflowTestUtil.pollAndCompleteTask('simple_task_in_sub_wf', 'subworkflow.task.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndCompletedSubWorkflowTask)

        and: "verify that the sub workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'simple_task_in_sub_wf'
        }

        and: "verify that the workflow is in a RUNNING state and sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].status == Task.Status.COMPLETED
        }

        when: "the simple task is polled and completed"
        def polledAndCompletedSimpleTask = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polledAndCompletedSimpleTask)

        and: "verify that the workflow is in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 5
            tasks[0].taskType == 'FORK'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'JOIN'
            tasks[3].inputData['joinOn'] == ['st1', 't2']
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'SUB_WORKFLOW'
            tasks[4].status == Task.Status.COMPLETED
        }
    }
}
