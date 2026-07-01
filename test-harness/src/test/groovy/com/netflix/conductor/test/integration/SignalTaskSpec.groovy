/*
 * Copyright 2025 Conductor Authors.
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
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.TaskService
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

class SignalTaskSpec extends AbstractSpecification {

    @Autowired
    TaskService taskService

    @Autowired
    QueueDAO queueDAO

    @Autowired
    SubWorkflow subWorkflowTask

    @Shared
    def WAIT_WORKFLOW = 'test_wait_workflow'

    @Shared
    def SIGNAL_SUB_WF_PARENT = 'signal_sub_wf_parent'

    @Shared
    def SIGNAL_SUB_WF_CHILD = 'signal_sub_wf_child'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'wait_workflow_integration_test.json',
                'signal_sub_wf_child_integration_test.json',
                'signal_sub_wf_parent_integration_test.json')
    }

    def "Signal completes a blocked WAIT task and advances the workflow"() {
        given: "a workflow blocked at a WAIT task"
        def workflowId = startWorkflow(WAIT_WORKFLOW, 1, '', [:], null)

        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.IN_PROGRESS
        }

        when: "the workflow is signaled with COMPLETED status"
        def taskId = taskService.signalTask(workflowId, TaskResult.Status.COMPLETED, [result: 'done'])

        then: "signalTask returns the id of the WAIT task that was updated"
        taskId != null

        and: "the WAIT task is now COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "the sweeper runs to advance the workflow to the next task"
        sweep(workflowId)

        then: "the simple task after WAIT is now SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }
    }

    def "Signal on a workflow with no blocking task returns null"() {
        given: "a workflow whose WAIT task has already been completed"
        def workflowId = startWorkflow(WAIT_WORKFLOW, 1, '', [:], null)
        def waitTask = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]
        waitTask.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(waitTask))

        when: "the workflow is signaled again"
        def taskId = taskService.signalTask(workflowId, TaskResult.Status.COMPLETED, [:])

        then: "signalTask returns null because there is no blocked task"
        taskId == null
    }

    def "Signal on a non-existent workflow returns null"() {
        when: "signaling a workflow id that does not exist"
        def taskId = taskService.signalTask('non-existent-wf-id', TaskResult.Status.COMPLETED, [:])

        then: "signalTask returns null rather than throwing"
        taskId == null
    }

    def "Signal descends into a sub-workflow to complete its blocked WAIT task"() {
        given: "a parent workflow containing a sub-workflow with a WAIT task"
        def parentWfId = startWorkflow(SIGNAL_SUB_WF_PARENT, 1, '', [:], null)

        and: "the SUB_WORKFLOW system task is executed to start the child workflow"
        List<String> polledTaskIds = queueDAO.pop('SUB_WORKFLOW', 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])

        with(workflowExecutionService.getExecutionStatus(parentWfId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].taskType == 'SUB_WORKFLOW'
            tasks[0].status == Task.Status.IN_PROGRESS
        }

        and: "the sub-workflow is swept so its WAIT task reaches IN_PROGRESS"
        def subWfId = workflowExecutionService.getExecutionStatus(parentWfId, true).tasks[0].subWorkflowId
        sweep(subWfId)

        with(workflowExecutionService.getExecutionStatus(subWfId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.IN_PROGRESS
        }

        when: "the parent workflow id is used to signal (descend-into-sub-wf path)"
        def taskId = taskService.signalTask(parentWfId, TaskResult.Status.COMPLETED, [result: 'sub-done'])

        then: "the id of the WAIT task inside the sub-workflow is returned"
        taskId != null

        and: "the WAIT task inside the sub-workflow is now COMPLETED"
        with(workflowExecutionService.getExecutionStatus(subWfId, true)) {
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "the sub-workflow is swept to terminal state"
        sweep(subWfId)

        then: "the sub-workflow completes"
        with(workflowExecutionService.getExecutionStatus(subWfId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
        }

        when: "the parent workflow is swept"
        sweep(parentWfId)

        then: "the parent workflow also completes"
        with(workflowExecutionService.getExecutionStatus(parentWfId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
        }
    }
}
