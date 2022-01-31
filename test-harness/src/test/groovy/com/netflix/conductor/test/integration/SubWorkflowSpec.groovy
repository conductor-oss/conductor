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
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class SubWorkflowSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    SubWorkflow subWorkflowTask

    @Shared
    def WORKFLOW_WITH_SUBWORKFLOW = 'integration_test_wf_with_sub_wf'

    @Shared
    def SUB_WORKFLOW = "sub_workflow"

    @Shared
    def SIMPLE_WORKFLOW = "integration_test_wf"

    def setup() {
        workflowTestUtil.registerWorkflows('simple_one_task_sub_workflow_integration_test.json',
                'simple_workflow_1_integration_test.json',
                'workflow_with_sub_workflow_1_integration_test.json')
    }

    def "Test retrying a subworkflow where parent workflow timed out due to workflowTimeout"() {

        setup: "Register a workflow definition with a timeout policy set to timeout workflow"
        def persistedWorkflowDefinition = metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1)
        def modifiedWorkflowDefinition = new WorkflowDef()
        modifiedWorkflowDefinition.name = persistedWorkflowDefinition.name
        modifiedWorkflowDefinition.version = persistedWorkflowDefinition.version
        modifiedWorkflowDefinition.tasks = persistedWorkflowDefinition.tasks
        modifiedWorkflowDefinition.inputParameters = persistedWorkflowDefinition.inputParameters
        modifiedWorkflowDefinition.outputParameters = persistedWorkflowDefinition.outputParameters
        modifiedWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.TIME_OUT_WF
        modifiedWorkflowDefinition.timeoutSeconds = 10
        modifiedWorkflowDefinition.ownerEmail = persistedWorkflowDefinition.ownerEmail
        metadataService.updateWorkflowDef([modifiedWorkflowDefinition])

        and: "an existing workflow with subworkflow and registered definitions"
        metadataService.getWorkflowDef(SUB_WORKFLOW, 1)
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'wf_with_subwf_test_1'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'
        input['subwf'] = 'sub_workflow'

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1,
                correlationId, input, null, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task"
        def pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        and: "verify that the 'integration_task1' is complete and the next task (subworkflow) is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the subworkflow is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        String subworkflowTaskId = polledTaskIds.get(0)
        asyncSystemTaskExecutor.execute(subWorkflowTask, subworkflowTaskId)

        then: "verify that the 'sub_workflow_task' is in a IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        when: "subworkflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.tasks[1].subWorkflowId

        then: "verify that the sub workflow is RUNNING, and first task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "a delay of 10 seconds is introduced and the workflow is sweeped to run the evaluation"
        Thread.sleep(10000)
        sweep(workflowInstanceId)

        then: "ensure that the workflow has been TIMED OUT and subworkflow task is CANCELED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TIMED_OUT
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.CANCELED
        }

        and: "ensure that the subworkflow is TERMINATED and task is CANCELED"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.CANCELED
        }

        when: "the subworkflow is retried"
        workflowExecutor.retry(subWorkflowId, false)

        then: "ensure that the subworkflow is RUNNING and task is retried"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.CANCELED
            tasks[1].taskType == 'simple_task_in_sub_wf'
            tasks[1].status == Task.Status.SCHEDULED
        }

        and: "verify that change flag is set on the sub workflow task in parent"
        workflowExecutionService.getTask(subworkflowTaskId).subworkflowChanged

        when: "Polled for simple_task_in_sub_wf task in subworkflow"
        pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('simple_task_in_sub_wf', 'task1.integration.worker', ['op': 'simple_task_in_sub_wf.done'])

        then: "verify that the 'simple_task_in_sub_wf' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        and: "verify that the subworkflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.CANCELED
            tasks[1].taskType == 'simple_task_in_sub_wf'
            tasks[1].status == Task.Status.COMPLETED
        }

        and: "subworkflow task is in a completed state"
        with(workflowExecutionService.getTask(subworkflowTaskId)) {
            status == Task.Status.COMPLETED
            subworkflowChanged
        }

        and: "the parent workflow is swept"
        sweep(workflowInstanceId)

        and: "the parent workflow has been resumed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.COMPLETED
            !tasks[1].subworkflowChanged
            output['op'] == 'simple_task_in_sub_wf.done'
        }

        cleanup: "Ensure that the changes to the workflow def are reverted"
        metadataService.updateWorkflowDef([persistedWorkflowDefinition])
    }

    def "Test terminating a subworkflow terminates parent workflow"() {
        given: "Existing workflow and subworkflow definitions"
        metadataService.getWorkflowDef(SUB_WORKFLOW, 1)
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'wf_with_subwf_test_1'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'
        input['subwf'] = 'sub_workflow'

        when: "Start a workflow with subworkflow based on the registered definition"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1,
                correlationId, input,
                null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Polled for integration_task_1 task"
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that the 'integration_task1' is complete and the next task (subworkflow) is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Polled for and executed subworkflow task"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.tasks[1].subWorkflowId

        then: "verify that the 'sub_workflow_task' is polled and IN_PROGRESS"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        and: "verify that the sub workflow is RUNNING, and first task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "subworkflow is terminated"
        def terminateReason = "terminating from a test case"
        workflowExecutor.terminateWorkflow(subWorkflowId, terminateReason)

        then: "verify that sub workflow is in terminated state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.CANCELED
            reasonForIncompletion == terminateReason
        }

        and:
        sweep(workflowInstanceId)

        and: "verify that parent workflow is in terminated state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.CANCELED
            reasonForIncompletion && reasonForIncompletion.contains(terminateReason)
        }
    }

    def "Test retrying a workflow with subworkflow resume"() {
        setup: "Modify task definition to 0 retries"
        def persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name, persistedTask2Definition.description,
                persistedTask2Definition.ownerEmail, 0, persistedTask2Definition.timeoutSeconds,
                persistedTask2Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        and: "an existing workflow with subworkflow and registered definitions"
        metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1)
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'wf_retry_with_subwf_resume_test'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'
        input['subwf'] = 'integration_test_wf'

        when: "the workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1,
                correlationId, input, null, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task"
        def pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        and: "verify that the 'integration_task_1' is complete and the next task (subworkflow) is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "the subworkflow is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])

        then: "verify that the 'sub_workflow_task' is in a IN_PROGRESS state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        when: "subworkflow is retrieved"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def subWorkflowId = workflow.tasks[1].subWorkflowId

        then: "verify that the sub workflow is RUNNING, and first task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task"
        pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        and: "verify that the 'integration_task_1' is complete and the next task is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and fail the integration_task_2 task"
        def pollAndFailTask = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failed')

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndFailTask)

        then: "the sub workflow ends up in a FAILED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
        }

        and:
        sweep(workflowInstanceId)

        and: "the workflow is in a FAILED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'SUB_WORKFLOW'
            tasks[1].status == Task.Status.FAILED
        }

        when: "the workflow is retried by resuming subworkflow task"
        workflowExecutor.retry(workflowInstanceId, true)

        then: "the subworkflow is in a RUNNING state"
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

        and: "the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        when: "poll and complete the integration_task_2 task"
        pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the 'integration_task_2' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        then: "the integration_task_2 is complete sub workflow ends up in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
        }

        and:
        sweep(workflowInstanceId)

        then: "the workflow is in a COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            !tasks[1].subworkflowChanged
        }

        cleanup: "Ensure that changes to the task def are reverted"
        metadataService.updateTaskDef(persistedTask2Definition)
    }
}
