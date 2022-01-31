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
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class DynamicForkJoinSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    SubWorkflow subWorkflowTask

    @Shared
    def DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest"

    def setup() {
        workflowTestUtil.registerWorkflows('dynamic_fork_join_integration_test.json',
                'simple_workflow_3_integration_test.json')
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
                persistedTask2Definition.description, persistedTask2Definition.ownerEmail, 0,
                persistedTask2Definition.timeoutSeconds, persistedTask2Definition.responseTimeoutSeconds)
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
                persistedTask2Definition.description, persistedTask2Definition.ownerEmail, 0,
                persistedTask2Definition.timeoutSeconds, persistedTask2Definition.responseTimeoutSeconds)
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
        workflowExecutor.retry(workflowInstanceId, false)

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

    def "Retry a failed dynamic fork join workflow with forked subworkflow"() {
        setup: "Make sure that the integration_task_2 does not have any retry count"
        def persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name,
                persistedTask2Definition.description, persistedTask2Definition.ownerEmail, 0,
                persistedTask2Definition.timeoutSeconds, persistedTask2Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        when: "the dynamic fork join workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1,
                'dynamic_fork_join_wf_subwf', [:], null, null, null)

        then: "verify that the workflow is started and first task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
        }

        when: "the first task's output has a list of dynamically forked tasks including a subworkflow"
        WorkflowTask workflowTask2 = new WorkflowTask()
        workflowTask2.name = 'sub_wf_task'
        workflowTask2.taskReferenceName = 'xdt1'
        workflowTask2.workflowTaskType = TaskType.SUB_WORKFLOW
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams()
        subWorkflowParams.setName("integration_test_wf3")
        subWorkflowParams.setVersion(1)
        workflowTask2.subWorkflowParam = subWorkflowParams

        WorkflowTask workflowTask3 = new WorkflowTask()
        workflowTask3.name = 'integration_task_10'
        workflowTask3.taskReferenceName = 'xdt10'

        def dynamicTasksInput = ['xdt1': ['p1': 'q1', 'p2': 'q2'], 'xdt10': ['k2': 'v2']]

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
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "the subworkflow is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        String subworkflowTaskId = polledTaskIds.get(0)
        asyncSystemTaskExecutor.execute(subWorkflowTask, subworkflowTaskId)

        then: "verify that the sub workflow task is in a IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "subworkflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.tasks[2].subWorkflowId

        then: "verify that the sub workflow is RUNNING, and first task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "The 'integration_task_10' is polled and completed"
        def pollAndCompleteTask10Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_10', 'task10.worker')

        then: "verify that the task was completed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask10Try1)

        and: "verify that the workflow is updated"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

       when: "The task within sub workflow is polled and completed"
        pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker')

        then: "verify that the task was completed"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "the next task in the subworkflow is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Poll and fail 'integration_task_2'"
        def pollAndCompleteTask2Try1 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.worker', "failure")

        and: "the workflow is evaluated"
        sweep(workflowInstanceId)

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1)

        and: "the subworkflow is in FAILED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
        }

        and: "the workflow is also in FAILED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "The workflow is retried"
        workflowExecutor.retry(workflowInstanceId, true)

        then: "verify that the workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.CANCELED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        and: "the subworkflow is retried and in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "the workflow is evaluated"
        sweep(workflowInstanceId)

        then: "verify that the JOIN is updated"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.IN_PROGRESS
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.IN_PROGRESS
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
        }

        when: "Poll and complete 'integration_task_2'"
        def pollAndCompleteTask2Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try2)

        and: "the sub workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete 'integration_task_3'"
        def pollAndCompleteTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.worker')

        then: "verify that the task was polled and acknowledged"
        workflowTestUtil.verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1)

        and: "the sub workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.COMPLETED
        }

        when: "the workflow is evaluated"
        sweep(workflowInstanceId)

        then: "the workflow has progressed beyond the join task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.COMPLETED
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
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
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            tasks[2].status == Task.Status.COMPLETED
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_10'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'JOIN'
            tasks[4].status == Task.Status.COMPLETED
            tasks[4].referenceTaskName == 'dynamicfanouttask_join'
            tasks[5].taskType == 'integration_task_4'
            tasks[5].status == Task.Status.COMPLETED
        }

        cleanup: "roll back the change made to integration_task_2 definition"
        metadataService.updateTaskDef(persistedTask2Definition)
    }
}
