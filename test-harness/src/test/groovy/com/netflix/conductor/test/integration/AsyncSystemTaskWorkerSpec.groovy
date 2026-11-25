/*
 * Copyright 2026 Conductor Authors.
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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSystemTaskWorkerSpecification

import spock.lang.Shared

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW

/**
 * Restores test-harness coverage of the async system-task poll/execute path, driven by the real
 * SystemTaskWorker.
 *
 * Every other sub-workflow spec obtains the SCHEDULED SUB_WORKFLOW task id itself and calls
 * asyncSystemTaskExecutor.execute(...) directly — it bought determinism by hand-driving the
 * executor, and as a result the queue hop is never exercised. Nothing here touches
 * asyncSystemTaskExecutor: the worker must find the message on its own.
 */
class AsyncSystemTaskWorkerSpec extends AbstractSystemTaskWorkerSpecification {

    @Autowired
    QueueDAO queueDAO

    @Shared
    def WORKFLOW_WITH_SUBWORKFLOW = 'integration_test_wf_with_sub_wf'

    @Shared
    def SUB_WORKFLOW = 'sub_workflow'

    def setup() {
        workflowTestUtil.registerWorkflows('simple_one_task_sub_workflow_integration_test.json',
                'workflow_with_sub_workflow_1_integration_test.json')
    }

    def "the real system task worker polls and starts the SUB_WORKFLOW task"() {
        given: "a parent workflow whose second task is a SUB_WORKFLOW"
        def input = ['param1': 'p1 value', 'param2': 'p2 value', 'subwf': SUB_WORKFLOW]

        when: "the parent workflow is started"
        def rootWorkflowId = startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, 'worker_poll_execute', input, null)

        then: "the first task is scheduled"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
                status == Workflow.WorkflowStatus.RUNNING
                tasks.size() == 1
                tasks[0].taskType == 'integration_task_1'
                tasks[0].status == Task.Status.SCHEDULED
            }
        }

        when: "the first task completes - and nothing else is driven by hand"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "the SystemTaskWorker pops the SUB_WORKFLOW message and starts the child workflow"
        String subWorkflowId = null
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
                tasks.size() == 2
                tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
                tasks[1].status == Task.Status.IN_PROGRESS
                tasks[1].subWorkflowId != null
            }
            subWorkflowId = workflowExecutionService.getExecutionStatus(rootWorkflowId, true).tasks[1].subWorkflowId
        }

        and: "the child workflow is running its own first task"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
                status == Workflow.WorkflowStatus.RUNNING
                tasks.size() == 1
                tasks[0].taskType == 'simple_task_in_sub_wf'
            }
        }
    }

    def "repeated sweeps do not duplicate an in-flight SUB_WORKFLOW task"() {
        given: "a parent workflow whose SUB_WORKFLOW task the worker has already started"
        def input = ['param1': 'p1 value', 'param2': 'p2 value', 'subwf': SUB_WORKFLOW]
        def rootWorkflowId = startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, 'worker_no_duplicate', input, null)

        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
                tasks.size() == 1
                tasks[0].status == Task.Status.SCHEDULED
            }
        }
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        String subWorkflowId = null
        String subWorkflowTaskId = null
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
                tasks.size() == 2
                tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
                tasks[1].status == Task.Status.IN_PROGRESS
                tasks[1].subWorkflowId != null
            }
            def subWorkflowTask = workflowExecutionService.getExecutionStatus(rootWorkflowId, true).tasks[1]
            subWorkflowId = subWorkflowTask.subWorkflowId
            subWorkflowTaskId = subWorkflowTask.taskId
        }

        when: "the parent is swept repeatedly while the SUB_WORKFLOW task is in flight"
        5.times { sweep(rootWorkflowId) }

        then: "exactly one child workflow exists - repair did not re-queue the running task"
        workflowExecutionService.getRunningWorkflows(SUB_WORKFLOW, 1).size() == 1

        and: "the parent still holds a single SUB_WORKFLOW task pointing at the same child"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            tasks.findAll { it.taskType == TASK_TYPE_SUB_WORKFLOW }.size() == 1
            tasks.find { it.taskType == TASK_TYPE_SUB_WORKFLOW }.subWorkflowId == subWorkflowId
        }

        and: "the in-flight task has no queue message, and repair left it that way (#202, #630)"
        !queueDAO.containsMessage(TASK_TYPE_SUB_WORKFLOW, subWorkflowTaskId)
    }
}
