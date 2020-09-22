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
package com.netflix.counductor.integration.test

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.workflow.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.execution.WorkflowRepairService
import com.netflix.conductor.core.execution.WorkflowSweeper
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

@ModulesForTesting([TestModule.class])
class SubWorkflowSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    MetadataService metadataService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowSweeper workflowSweeper

    @Inject
    WorkflowRepairService workflowRepairService

    @Inject
    WorkflowTestUtil workflowTestUtil

    @Inject
    QueueDAO queueDAO

    @Shared
    def WORKFLOW_WITH_SUBWORKFLOW = 'integration_test_wf_with_sub_wf'

    @Shared
    def SUB_WORKFLOW = "sub_workflow"

    def setup() {
        workflowTestUtil.registerWorkflows('simple_one_task_sub_workflow_integration_test.json',
                'workflow_with_sub_workflow_1_integration_test.json')
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
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
        WorkflowSystemTask systemTask = SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.get("SUB_WORKFLOW")
        workflowExecutor.executeSystemTask(systemTask, polledTaskIds.get(0), 30)

        then: "verify that the 'sub_workflow_task' is in a IN_PROGRESS"
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
        workflowSweeper.sweep([workflowInstanceId], workflowExecutor, workflowRepairService)

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
        workflowExecutor.retry(subWorkflowId)

        then: "ensure that the subworkflow is RUNNING and task is retried"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'simple_task_in_sub_wf'
            tasks[0].status == Task.Status.CANCELED
            tasks[1].taskType == 'simple_task_in_sub_wf'
            tasks[1].status == Task.Status.SCHEDULED
        }

        and: "the parent workflow has been resumed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.IN_PROGRESS
        }

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

        and: "the parent workflow has been resumed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TaskType.SUB_WORKFLOW.name()
            tasks[1].status == Task.Status.COMPLETED
            output['op'] == 'simple_task_in_sub_wf.done'
        }
    }
}
