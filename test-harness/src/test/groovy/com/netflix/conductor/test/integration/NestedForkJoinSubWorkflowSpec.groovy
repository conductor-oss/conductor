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
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW

class NestedForkJoinSubWorkflowSpec extends AbstractSpecification {

    @Shared
    def FORK_JOIN_NESTED_SUB_WF = 'nested_fork_join_swf'

    @Shared
    def SIMPLE_WORKFLOW = "integration_test_wf"

    @Autowired
    QueueDAO queueDAO

    @Autowired
    SubWorkflow subWorkflowTask

    String parentWorkflowId, subworkflowId

    TaskDef persistedTask2Definition

    def setup() {
        workflowTestUtil.registerWorkflows('nested_fork_join_swf.json',
                'simple_workflow_1_integration_test.json'
        )

        //region Test setup: 3 workflows reach FAILED state. Task 'integration_task_2' in leaf workflow is FAILED.
        setup: "Modify task definition to 0 retries"
        persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name, persistedTask2Definition.description,
                persistedTask2Definition.ownerEmail, 0, persistedTask2Definition.timeoutSeconds,
                persistedTask2Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        and: "an existing workflow with subworkflow and registered definitions"
        metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1)
        metadataService.getWorkflowDef(FORK_JOIN_NESTED_SUB_WF, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'retry_on_root_in_3level_wf'
        def input = [
                'param1'   : 'p1 value',
                'param2'   : 'p2 value',
                'subwf'    : SIMPLE_WORKFLOW]

        when: "the workflow is started"
        parentWorkflowId = workflowExecutor.startWorkflow(FORK_JOIN_NESTED_SUB_WF, 1,
                correlationId, input, null, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "poll and complete the integration_task_2 task"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds.get(0))

        then: "verify that the 'sub_workflow_task' is in a IN_PROGRESS state"
        def parentWorkflowInstance = workflowExecutionService.getExecutionStatus(parentWorkflowId, true)
        with(parentWorkflowInstance) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        and: "verify that the mid-level workflow is RUNNING, and first task is in SCHEDULED state"
        subworkflowId = parentWorkflowInstance.tasks[2].subWorkflowId
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "poll and fail the integration_task_2 task in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'task2 failed')

        then: "the sub workflow ends up in a FAILED state"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.CANCELED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.CANCELED
        }
        //endregion
    }

    def cleanup() {
        metadataService.updateTaskDef(persistedTask2Definition)
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED task
     * in the sub workflow.
     *
     * A restart is executed on the sub workflow.
     *
     * Expectation: The sub workflow spawns a execution with the same id.
     * When the sub workflow completes successfully, the parent workflow also completes successfully.
     */
    def "test restart on the sub workflow in a nested fork join workflow"() {
        when:
        workflowExecutor.restart(subworkflowId, false)

        then: "verify that the subworkflow is RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "verify that the parent workflow is updated"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.CANCELED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.CANCELED
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify that the flag is reset and JOIN is updated"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "poll and complete both tasks in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the subworkflow completed"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        and: "verify that the parent workflow's sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify that the parent workflow reaches COMPLETED with all tasks completed"
        assertParentWorkflowIsComplete()
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED task
     * in the sub workflow.
     *
     * A restart is executed on the parent workflow.
     *
     * Expectation: The parent workflow spawns a execution with the same id, which in turn creates a new instance of the sub workflow.
     * When the sub workflow completes successfully, the parent workflow also completes successfully.
     */
    def "test restart on the parent workflow in a nested fork join workflow"() {
        when:
        workflowExecutor.restart(parentWorkflowId, false)

        then: "verify that the parent workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.SCHEDULED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds.get(0))
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that SUB_WORKFLOW task in in progress"
        def parentWorkflowInstance = workflowExecutionService.getExecutionStatus(parentWorkflowId, true)
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        and: "verify that a new instance of the sub workflow is created"
        def newSubWorkflowId = parentWorkflowInstance.tasks[2].subWorkflowId
        newSubWorkflowId != subworkflowId
        with(workflowExecutionService.getExecutionStatus(newSubWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete both tasks in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the subworkflow completed"
        with(workflowExecutionService.getExecutionStatus(newSubWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        and: "verify that the parent workflow's sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify that the parent workflow reaches COMPLETED with all tasks completed"
        assertParentWorkflowIsComplete()
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED task
     * in the sub workflow.
     *
     * A retry is executed on the parent workflow.
     *
     * Expectation: The parent workflow spawns a execution with the same id, which in turn creates a new instance of the sub workflow.
     * When the sub workflow completes successfully, the parent workflow also completes successfully.
     */
    def "test retry on the parent workflow in a nested fork join workflow"() {
        when:
        workflowExecutor.retry(parentWorkflowId, false)

        then: "verify that the parent workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 8
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.FAILED
            tasks[2].retried
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[7].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[7].status == Task.Status.SCHEDULED
            tasks[7].retriedTaskId == tasks[2].taskId
        }

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds.get(0))

        then: "verify that SUB_WORKFLOW task in in progress"
        def parentWorkflowInstance = workflowExecutionService.getExecutionStatus(parentWorkflowId, true)
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 8
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.FAILED
            tasks[2].retried
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[7].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[7].status == Task.Status.IN_PROGRESS
            tasks[7].retriedTaskId == tasks[2].taskId
        }

        and: "verify that a new instance of the sub workflow is created"
        def newSubWorkflowId = parentWorkflowInstance.tasks[7].subWorkflowId
        newSubWorkflowId != subworkflowId
        with(workflowExecutionService.getExecutionStatus(newSubWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete both tasks in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the subworkflow completed"
        with(workflowExecutionService.getExecutionStatus(newSubWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        and: "verify that the parent workflow's sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 8
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.FAILED
            tasks[2].retried
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
            tasks[7].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[7].status == Task.Status.COMPLETED
            tasks[7].retriedTaskId == tasks[2].taskId
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify that the parent workflow reaches COMPLETED with all tasks completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 8
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.FAILED
            tasks[2].retried
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[7].status == Task.Status.COMPLETED
            tasks[7].retriedTaskId == tasks[2].taskId
        }
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED task
     * in the sub workflow.
     *
     * A retry with resume flag is executed on the parent workflow.
     *
     * Expectation: The parent workflow spawns a execution with the same id, which in turn creates a new instance of the sub workflow.
     * When the sub workflow completes successfully, the parent workflow also completes successfully.
     */
    def "test retry with resume on the parent workflow in a nested fork join workflow"() {
        when:
        workflowExecutor.retry(parentWorkflowId, true)

        then: "verify that the sub workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        and: "verify that the parent's SUB_WORKFLOW task is updated"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.CANCELED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.CANCELED
        }

        when: "the parent is swept"
        sweep(parentWorkflowId)

        then: "verify that parent's JOIN task in in progress"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "poll and complete the failed task in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the subworkflow completed"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        and: "verify that the parent workflow's sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify the parent workflow reaches COMPLETED state"
        assertParentWorkflowIsComplete()
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED task
     * in the sub workflow.
     *
     * A retry is executed on the parent workflow.
     *
     * Expectation: The parent workflow spawns a execution with the same id, which in turn creates a new instance of the sub workflow.
     * When the sub workflow completes successfully, the parent workflow also completes successfully.
     */
    def "test retry on the sub workflow in a nested fork join workflow"() {
        when:
        workflowExecutor.retry(subworkflowId, false)

        then: "verify that the sub workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        and: "verify that the parent's SUB_WORKFLOW task is updated"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.CANCELED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.CANCELED
        }

        when: "the parent is swept"
        sweep(parentWorkflowId)

        then: "verify that parent's JOIN task in in progress"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.IN_PROGRESS
            !tasks[2].subworkflowChanged
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "poll and complete the failed task in the sub workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "verify that the subworkflow completed"
        with(workflowExecutionService.getExecutionStatus(subworkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        and: "verify that the parent workflow's sub workflow task is completed"
        with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.IN_PROGRESS
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "the parent workflow is swept"
        sweep(parentWorkflowId)

        then: "verify the parent workflow reaches COMPLETED state"
        assertParentWorkflowIsComplete()
    }

    private void assertParentWorkflowIsComplete() {
        assert with(workflowExecutionService.getExecutionStatus(parentWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 7
            tasks[0].taskType == TASK_TYPE_FORK
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_FORK
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == TASK_TYPE_JOIN
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == TASK_TYPE_JOIN
            tasks[6].status == Task.Status.COMPLETED
        }
    }
}
