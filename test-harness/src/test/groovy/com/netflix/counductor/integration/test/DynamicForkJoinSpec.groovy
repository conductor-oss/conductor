/**
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
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.metadata.MetadataMapperService
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@ModulesForTesting([TestModule.class])
class DynamicForkJoinSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    MetadataService metadataService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowTestUtil workflowTestUtil

    @Inject
    MetadataMapperService metadataMapperService

    @Shared
    def DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest"


    def setup() {
        workflowTestUtil.registerWorkflows('dynamic_fork_join_integration_test.json')
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
    }


    def "Test dynamic fork join success flow"() {
        when: " a dynamic fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1,
                'dynamic_fork_join_workflow', [:],
                null, null, null)

        then: "verify that the workflow has been successfully started and the first task is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: " the first task is 'integration_task_1' output has a list of dynamic tasks"
        WorkflowTask workflowTask2 = new WorkflowTask()
        workflowTask2.name = 'integration_task_2'
        workflowTask2.taskReferenceName = 'xdt1'

        WorkflowTask workflowTask3 = new WorkflowTask()
        workflowTask3.name = 'integration_task_3'
        workflowTask3.taskReferenceName = 'xdt2'

        def dynamicTasksInput = ['xdt1': ['k1': 'v1'], 'xdt2': ['k2': 'v2']]

        and: "The 'integration_task_1' is polled and completed"
        def pollAndCompleteTask1Try = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker',
                ['dynamicTasks': [workflowTask2, workflowTask3], 'dynamicTasksInput': dynamicTasksInput])

        then: "verify that the task was completed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try)

        and: "verify that workflow has progressed further ahead and new dynamic tasks have been scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "Poll and complete 'integration_task_2' and 'integration_task_3'"
        def pollAndCompleteTask2Try = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker',
                ['ok1': 'ov1'])
        def pollAndCompleteTask3Try = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.worker',
                ['ok1': 'ov1'])

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try, ['k1': 'v1'])
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try, ['k2': 'v2'])

        and: "verify that the workflow has progressed and the 'integration_task_2' and 'integration_task_3' are complete"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['xdt1', 'xdt2']
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
            tasks[4].outputData['xdt1']['ok1'] == 'ov1'
            tasks[4].outputData['xdt2']['ok1'] == 'ov1'
            tasks[5].taskType == 'integration_task_4'
            tasks[5].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete 'integration_task_4'"
        def pollAndCompleteTask4Try = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4.worker')

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try)

        and: "verify that the workflow is complete"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[5].taskType == 'integration_task_4'
            tasks[5].status == Task.Status.COMPLETED
        }
    }


    def "Test dynamic fork join failure of dynamic forked task flow"() {
        setup: "Make sure that the integration_task_2 does not have any retry count"
        def persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name,
                persistedTask2Definition.description, 0, persistedTask2Definition.timeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        when: " a dynamic fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1,
                'dynamic_fork_join_workflow', [:],
                null, null, null)

        then: "verify that the workflow has been successfully started and the first task is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: " the first task is 'integration_task_1' output has a list of dynamic tasks"
        WorkflowTask workflowTask2 = new WorkflowTask()
        workflowTask2.name = 'integration_task_2'
        workflowTask2.taskReferenceName = 'xdt1'

        WorkflowTask workflowTask3 = new WorkflowTask()
        workflowTask3.name = 'integration_task_3'
        workflowTask3.taskReferenceName = 'xdt2'

        def dynamicTasksInput = ['xdt1': ['k1': 'v1'], 'xdt2': ['k2': 'v2']]

        and: "The 'integration_task_1' is polled and completed"
        def pollAndCompleteTask1Try = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker',
                ['dynamicTasks': [workflowTask2, workflowTask3], 'dynamicTasksInput': dynamicTasksInput])

        then: "verify that the task was completed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try)

        and: "verify that workflow has progressed further ahead and new dynamic tasks have been scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "Poll and fail 'integration_task_2'"
        def pollAndCompleteTask2Try = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.worker', 'it is a failure..')

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try, ['k1': 'v1'])

        and: "verify that the workflow is in failed state and 'integration_task_2' has also failed and other tasks are canceled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 5
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.CANCELED
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['xdt1', 'xdt2']
            tasks[4].status == Task.Status.CANCELED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        cleanup: "roll back the change made to integration_task_2 definition"
        metadataService.updateTaskDef(persistedTask2Definition)
    }


    def "Retry a failed dynamic fork join workflow"() {
        setup: "Make sure that the integration_task_2 does not have any retry count"
        def persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name,
                persistedTask2Definition.description, 0, persistedTask2Definition.timeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        when: " a dynamic fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1,
                'dynamic_fork_join_workflow', [:],
                null, null, null)

        then: "verify that the workflow has been successfully started and the first task is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: " the first task is 'integration_task_1' output has a list of dynamic tasks"
        WorkflowTask workflowTask2 = new WorkflowTask()
        workflowTask2.name = 'integration_task_2'
        workflowTask2.taskReferenceName = 'xdt1'

        WorkflowTask workflowTask3 = new WorkflowTask()
        workflowTask3.name = 'integration_task_3'
        workflowTask3.taskReferenceName = 'xdt2'

        def dynamicTasksInput = ['xdt1': ['k1': 'v1'], 'xdt2': ['k2': 'v2']]

        and: "The 'integration_task_1' is polled and completed"
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker',
                ['dynamicTasks': [workflowTask2, workflowTask3], 'dynamicTasksInput': dynamicTasksInput])

        then: "verify that the task was completed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that workflow has progressed further ahead and new dynamic tasks have been scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "Poll and fail 'integration_task_2'"
        def pollAndCompleteTask2Try1 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.worker', 'it is a failure..')

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1, ['k1': 'v1'])

        and: "verify that the workflow is in failed state and 'integration_task_2' has also failed and other tasks are canceled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 5
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.CANCELED
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['xdt1', 'xdt2']
            tasks[4].status == Task.Status.CANCELED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "The workflow is retried"
        workflowExecutor.retry(workflowInstanceId)

        then: "verify that the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.CANCELED
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['xdt1', 'xdt2']
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'integration_task_3'
            tasks[6].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete 'integration_task_2' and 'integration_task_3'"
        def pollAndCompleteTask2Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker',
                ['ok1': 'ov1'])
        def pollAndCompleteTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.worker',
                ['ok1': 'ov1'])

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try2, ['k1': 'v1'])
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1, ['k2': 'v2'])

        and: "verify that the workflow has progressed and the 'integration_task_2' and 'integration_task_3' are complete"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 8
            tasks[4].taskType == 'JOIN'
            tasks[4].inputData['joinOn'] == ['xdt1', 'xdt2']
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
            tasks[4].outputData['xdt1']['ok1'] == 'ov1'
            tasks[4].outputData['xdt2']['ok1'] == 'ov1'
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_3'
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == 'integration_task_4'
            tasks[7].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete 'integration_task_4'"
        def pollAndCompleteTask4Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4.worker')

        then: "verify that the tasks were polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask4Try1)

        and: "verify that the workflow is complete"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 8
            tasks[7].taskType == 'integration_task_4'
            tasks[7].status == Task.Status.COMPLETED
        }

        cleanup: "roll back the change made to integration_task_2 definition"
        metadataService.updateTaskDef(persistedTask2Definition)
    }

}
