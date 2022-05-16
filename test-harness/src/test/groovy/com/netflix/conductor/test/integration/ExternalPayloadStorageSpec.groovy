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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.MockExternalPayloadStorage
import com.netflix.conductor.test.utils.UserTask

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedLargePayloadTask
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class ExternalPayloadStorageSpec extends AbstractSpecification {

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Shared
    def CONDITIONAL_SYSTEM_TASK_WORKFLOW = 'ConditionalSystemWorkflow'

    @Shared
    def FORK_JOIN_WF = 'FanInOutTest'

    @Shared
    def DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest"

    @Shared
    def WORKFLOW_WITH_INLINE_SUB_WF = 'WorkflowWithInlineSubWorkflow'

    @Shared
    def WORKFLOW_WITH_DECISION_AND_TERMINATE = 'ConditionalTerminateWorkflow'

    @Shared
    def WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK = 'workflow_with_synchronous_system_task'

    @Autowired
    UserTask userTask

    @Autowired
    SubWorkflow subWorkflowTask

    @Autowired
    MockExternalPayloadStorage mockExternalPayloadStorage

    def setup() {
        workflowTestUtil.registerWorkflows('simple_workflow_1_integration_test.json',
                'conditional_system_task_workflow_integration_test.json',
                'fork_join_integration_test.json',
                'simple_workflow_with_sub_workflow_inline_def_integration_test.json',
                'decision_and_terminate_integration_test.json',
                'workflow_with_synchronous_system_task.json',
                'dynamic_fork_join_integration_test.json'
        )
    }

    def "Test simple workflow using external payload storage"() {

        given: "An existing simple workflow definition"
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        and: "input required to start large payload workflow"
        def correlationId = 'wf_external_storage'
        String workflowInputPath = uploadInitialWorkflowInput()

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_2' with external payload storage"
        pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask("integration_task_2", "task2.integration.worker", "")

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        then: "verify that the 'integration_task_2' is complete and the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            output.isEmpty()

            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED

        }
    }

    def "Test workflow with synchronous system task using external payload storage"() {
        given: "An existing workflow definition with sync system task followed by a simple task"
        metadataService.getWorkflowDef(WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK, 1)

        and: "input required to start large payload workflow"
        def correlationId = 'wf_external_storage'
        String workflowInputPath = uploadInitialWorkflowInput()

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_SYNCHRONOUS_SYSTEM_TASK, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == 'JSON_JQ_TRANSFORM'
            tasks[1].status == Task.Status.COMPLETED

            tasks[1].outputData['result'] == 104 // output of .tp2.TEST_SAMPLE | length expression from output.json. On assertion failure, check workflow definition and output.json
        }
    }

    def "Test conditional workflow with system task using external payload storage"() {

        given: "An existing workflow definition"
        metadataService.getWorkflowDef(CONDITIONAL_SYSTEM_TASK_WORKFLOW, 1)

        and: "input required to start large payload workflow"
        String workflowInputPath = uploadInitialWorkflowInput()
        def correlationId = "conditional_system_external_storage"

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(CONDITIONAL_SYSTEM_TASK_WORKFLOW, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == "DECISION"
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == "USER_TASK"
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].inputData.isEmpty()

        }

        when: "the system task 'USER_TASK' is started by issuing a system task call"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def taskId = workflow.getTaskByRefName('user_task').taskId
        asyncSystemTaskExecutor.execute(userTask, taskId)

        then: "verify that the user task is in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == "DECISION"
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == "USER_TASK"
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].inputData.isEmpty()

            tasks[2].outputData.get("size") == 104
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.SCHEDULED
        }

        when: "poll and complete and 'integration_task_3'"
        def pollAndCompleteTask3 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.integration.worker',
                ['op': 'success_task3'])

        then: "verify that the 'integration_task_3' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3)

        then: "verify that the 'integration_task_3' is complete and the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            output.isEmpty()

            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == "DECISION"
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == "USER_TASK"
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].inputData.isEmpty()

            tasks[2].outputData.get("size") == 104
            tasks[3].taskType == 'integration_task_3'
            tasks[3].status == Task.Status.COMPLETED
        }
    }

    def "Test fork join workflow using external payload storage"() {

        given: "An existing fork join workflow definition"
        metadataService.getWorkflowDef(FORK_JOIN_WF, 1)

        and: "input required to start large payload workflow"
        def correlationId = 'fork_join_external_storage'
        String workflowInputPath = uploadInitialWorkflowInput()

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(FORK_JOIN_WF, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
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

        when: "the first task of the left fork is polled and completed"
        def polledAndAckTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndAckTask)

        and: "task is completed and the next task in the fork is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_3'
        }

        when: "the first task of the right fork is polled and completed with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def polledAndAckLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_2', 'task2.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask)

        and: "task is completed and the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 5
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'JOIN'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_3'
        }

        when: "the second task of the left fork is polled and completed with external payload storage"
        polledAndAckLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_3', 'task3.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_3' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(polledAndAckLargePayloadTask)

        and: "task is completed and the next task after join in scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].outputData.isEmpty()

            tasks[3].status == Task.Status.COMPLETED
            tasks[3].taskType == 'JOIN'
            tasks[3].outputData.isEmpty()

            tasks[4].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_3'
            tasks[4].outputData.isEmpty()

            tasks[5].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'integration_task_4'
        }

        when: "the task 'integration_task_4' is polled and completed"
        polledAndAckTask = workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4.integration.worker')

        then: "verify that the 'integration_task_4' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndAckTask)

        and: "task is completed and the workflow is in completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'FORK'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].outputData.isEmpty()

            tasks[3].status == Task.Status.COMPLETED
            tasks[3].taskType == 'JOIN'
            tasks[3].outputData.isEmpty()

            tasks[4].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_3'
            tasks[4].outputData.isEmpty()

            tasks[5].status == Task.Status.COMPLETED
            tasks[5].taskType == 'integration_task_4'
        }
    }

    def "Test workflow with subworkflow using external payload storage"() {

        given: "An existing workflow definition"
        metadataService.getWorkflowDef(WORKFLOW_WITH_INLINE_SUB_WF, 1)

        and: "input required to start large payload workflow"
        String workflowInputPath = uploadInitialWorkflowInput()
        def correlationId = "workflow_with_inline_sub_wf_external_storage"

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_INLINE_SUB_WF, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].inputData.isEmpty()

        }

        when: "the subworkflow is started by issuing a system task call"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowTaskId = workflow.getTaskByRefName('swt').taskId
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWorkflowTaskId)

        then: "verify that the sub workflow task is in a IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].inputData.isEmpty()

        }

        when: "sub workflow is retrieved"
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowInstanceId = workflow.getTaskByRefName('swt').subWorkflowId

        then: "verify that the sub workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            input.isEmpty()

            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'integration_task_3'
        }

        when: "poll and complete the 'integration_task_3' with external payload storage"
        pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_3', 'task3.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_3' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the sub workflow is completed"
        with(workflowExecutionService.getExecutionStatus(subWorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            input.isEmpty()

            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_3'
            tasks[0].outputData.isEmpty()

            output.isEmpty()

        }

        and: "the subworkflow task is completed and the workflow is in running state"
        sweep(workflowInstanceId)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].inputData.isEmpty()

            tasks[1].outputData.isEmpty()

            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].inputData.isEmpty()

        }

        when: "poll and complete the 'integration_task_2' with external payload storage"
        pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_2', 'task2.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the task is completed and the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            output.isEmpty()

            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].inputData.isEmpty()

            tasks[1].outputData.isEmpty()

            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].inputData.isEmpty()

            tasks[2].outputData.isEmpty()

        }
    }

    def "Test retry workflow using external payload storage"() {

        setup: "Modify the task definition"
        def persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name, persistedTask2Definition.description,
                persistedTask2Definition.ownerEmail, 2, persistedTask2Definition.timeoutSeconds,
                persistedTask2Definition.responseTimeoutSeconds)
        modifiedTask2Definition.setRetryDelaySeconds(0)
        metadataService.updateTaskDef(modifiedTask2Definition)

        and: "an existing simple workflow definition"
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        and: "input required to start large payload workflow"
        def correlationId = 'retry_wf_external_storage'
        String workflowInputPath = uploadInitialWorkflowInput()

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED

        }

        when: "poll and fail the 'integration_task_2'"
        def pollAndFailTask2Try1 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failed')

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndFailTask2Try1)

        and: "verify that task is retried and workflow is still running"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].inputData.isEmpty()

            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].inputData.isEmpty()

        }

        when: "poll and complete the retried 'integration_task_2'"
        def pollAndCompleteTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'success_task2'])

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2)

        and: "verify that the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            output.isEmpty()

            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].inputData.isEmpty()

            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].inputData.isEmpty()

        }

        cleanup:
        metadataService.updateTaskDef(persistedTask2Definition)
    }

    def "Test workflow with terminate in decision branch using external payload storage"() {

        given: "An existing workflow definition"
        metadataService.getWorkflowDef(WORKFLOW_WITH_DECISION_AND_TERMINATE, 1)

        and: "input required to start large payload workflow"
        String workflowInputPath = uploadInitialWorkflowInput()
        def correlationId = "decision_terminate_external_storage"

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_DECISION_AND_TERMINATE, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].seq == 1
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = uploadLargeTaskOutput()
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that the 'integration_task_1' is COMPLETED and the workflow has FAILED due to terminate task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 3
            output.isEmpty()

            reasonForIncompletion.contains('Workflow is FAILED by TERMINATE task')
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()

            tasks[0].seq == 1
            tasks[1].taskType == 'DECISION'
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].seq == 2
            tasks[2].taskType == 'TERMINATE'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].inputData.isEmpty()

            tasks[2].seq == 3
            tasks[2].outputData.isEmpty()
        }
    }

    def "Test dynamic fork join workflow with subworkflow using external payload storage"() {
        given: "An existing dynamic fork join workflow definition"
        metadataService.getWorkflowDef(DYNAMIC_FORK_JOIN_WF, 1)

        and: "input required to start large payload workflow"
        def correlationId = "dynamic_fork_join_subworkflow_external_storage"
        String workflowInputPath = uploadInitialWorkflowInput()

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1, correlationId, null, workflowInputPath, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            input.isEmpty()

            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 'integration_task_1' with external payload storage"
        String taskOutputPath = "${UUID.randomUUID()}.json"
        mockExternalPayloadStorage.upload(taskOutputPath, mockExternalPayloadStorage.curateDynamicForkLargePayload())
        def pollAndCompleteLargePayloadTask = workflowTestUtil.pollAndCompleteLargePayloadTask('integration_task_1', 'task1.integration.worker', taskOutputPath)

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedLargePayloadTask(pollAndCompleteLargePayloadTask)

        and: "verify that workflow has progressed further ahead and new dynamic tasks have been scheduled with externalized payloads"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        with(workflow) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            !tasks[0].outputData
            tasks[1].taskType == 'FORK'
            !tasks[1].inputData

            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'SUB_WORKFLOW'
            !tasks[2].inputData

            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'JOIN'
            tasks[3].status == Task.Status.IN_PROGRESS
            tasks[3].referenceTaskName == 'dynamicfanouttask_join'
        }
    }

    private String uploadLargeTaskOutput() {
        String taskOutputPath = "${UUID.randomUUID()}.json"
        mockExternalPayloadStorage.upload(taskOutputPath, mockExternalPayloadStorage.readOutputDotJson(), 0)
        return taskOutputPath
    }

    private String uploadInitialWorkflowInput() {
        String workflowInputPath = "${UUID.randomUUID()}.json"
        mockExternalPayloadStorage.upload(workflowInputPath, ['param1': 'p1 value', 'param2': 'p2 value', 'case': 'two'])
        return workflowInputPath
    }
}
