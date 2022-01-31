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

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class SubWorkflowRetrySpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    SubWorkflow subWorkflowTask

    @Shared
    def WORKFLOW_WITH_SUBWORKFLOW = 'integration_test_wf_with_sub_wf'

    @Shared
    def SIMPLE_WORKFLOW = "integration_test_wf"

    String rootWorkflowId, midLevelWorkflowId, leafWorkflowId

    TaskDef persistedTask2Definition

    def setup() {
        workflowTestUtil.registerWorkflows('simple_one_task_sub_workflow_integration_test.json',
                'simple_workflow_1_integration_test.json',
                'workflow_with_sub_workflow_1_integration_test.json')

        //region Test setup: 3 workflows reach FAILED state. Task 'integration_task_2' in leaf workflow is FAILED.
        setup: "Modify task definition to 0 retries"
        persistedTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTask2Definition = new TaskDef(persistedTask2Definition.name, persistedTask2Definition.description,
                persistedTask2Definition.ownerEmail, 0, persistedTask2Definition.timeoutSeconds,
                persistedTask2Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTask2Definition)

        and: "an existing workflow with subworkflow and registered definitions"
        metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1)
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'retry_on_root_in_3level_wf'
        def input = [
                'param1'   : 'p1 value',
                'param2'   : 'p2 value',
                'subwf'    : WORKFLOW_WITH_SUBWORKFLOW,
                'nextSubwf': SIMPLE_WORKFLOW]

        when: "the workflow is started"
        rootWorkflowId = workflowExecutor.startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1,
                correlationId, input, null, null, null)

        then: "verify that the workflow is in a RUNNING state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task"
        def pollAndCompleteTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask)

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop("SUB_WORKFLOW", 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])

        then: "verify that the 'sub_workflow_task' is in a IN_PROGRESS state"
        def rootWorkflowInstance = workflowExecutionService.getExecutionStatus(rootWorkflowId, true)
        with(rootWorkflowInstance) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
        }

        and: "verify that the mid-level workflow is RUNNING, and first task is in SCHEDULED state"
        midLevelWorkflowId = rootWorkflowInstance.tasks[1].subWorkflowId
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "poll and complete the integration_task_1 task in the mid-level workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        polledTaskIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])
        def midLevelWorkflowInstance = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)

        then: "verify that the mid-level workflow is RUNNING, and first task is in SCHEDULED state"
        leafWorkflowId = midLevelWorkflowInstance.tasks[1].subWorkflowId
        def leafWorkflowInstance = workflowExecutionService.getExecutionStatus(leafWorkflowId, true)
        with(leafWorkflowInstance) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and fail the integration_task_2 task"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failed')

        then: "the leaf workflow ends up in a FAILED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
        }

        when: "the mid level workflow is 'decided'"
        sweep(midLevelWorkflowId)

        then: "the mid level subworkflow is in FAILED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
        }

        when: "the root level workflow is 'decided'"
        sweep(rootWorkflowId)

        then: "the root level workflow is in FAILED state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed on the root workflow.
     *
     * Expectation: The root workflow spawns a NEW mid-level workflow, which in turn spawns a NEW leaf workflow.
     * When the leaf workflow completes successfully, both the NEW mid-level and root workflows also complete successfully.
     */
    def "Test retry on the root in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the root workflow"
        workflowExecutor.retry(rootWorkflowId, false)

        then: "poll and complete the 'integration_task_1' task"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op1': 'task1.done'])

        and: "verify that the root workflow created a new SUB_WORKFLOW task"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the subworkflow task should be in SCHEDULED state and is started by issuing a system task call"
        def polledTaskIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])
        def newMidLevelWorkflowId = workflowExecutionService.getTask(polledTaskIds[0]).subWorkflowId

        then: "verify that a new mid level workflow is created and is in RUNNING state"
        newMidLevelWorkflowId != midLevelWorkflowId
        with(workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the integration_task_1 task in the mid-level workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        and: "poll and execute the sub workflow task"
        polledTaskIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])
        def newLeafWorkflowId = workflowExecutionService.getTask(polledTaskIds[0]).subWorkflowId

        then: "verify that a new leaf workflow is created and is in RUNNING state"
        newLeafWorkflowId != leafWorkflowId
        with(workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the two tasks in the leaf workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', ['op': 'task2.done'])

        then: "the new leaf workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        when: "the new mid level and root workflows are 'decided'"
        sweep(newMidLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "the new mid level workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
        }

        then: "the root workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed with resume flag on the root workflow.
     *
     * Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent (root) workflows are also retried.
     * When the leaf workflow completes successfully, both the mid-level and root workflows also complete successfully.
     */
    def "Test retry on the root with resume flag in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the root workflow"
        workflowExecutor.retry(rootWorkflowId, true)

        then: "verify that the sub workflow task in root workflow is updated"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the sub workflow task in mid level workflow is updated"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the previously failed task in leaf workflow is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "the mid level workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            !tasks[1].subworkflowChanged // flag is reset after "decide"
        }

        and: "the root workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            !tasks[1].subworkflowChanged // flag is reset after "decide"
        }

        when: "poll and complete the integration_task_2 task"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the leaf workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "the new mid level workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
        }

        and: "the root workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed on the mid-level workflow.
     *
     * Expectation: The mid-level workflow spawns a NEW leaf workflow and also updates its parent (root workflow).
     * When the NEW leaf workflow completes successfully, both the mid-level and root workflows also complete successfully.
     */
    def "Test retry on the mid-level in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the mid level workflow"
        workflowExecutor.retry(midLevelWorkflowId, false)

        then: "verify that the mid workflow created a new SUB_WORKFLOW task"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        and: "verify the SUB_WORKFLOW task in root workflow is updated"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        when: "the SUB_WORKFLOW task in mid level workflow is started by issuing a system task call"
        def polledTaskIds = queueDAO.pop(TASK_TYPE_SUB_WORKFLOW, 1, 200)
        asyncSystemTaskExecutor.execute(subWorkflowTask, polledTaskIds[0])
        def newLeafWorkflowId = workflowExecutionService.getTask(polledTaskIds[0]).subWorkflowId

        then: "verify that a new leaf workflow is created and is in RUNNING state"
        newLeafWorkflowId != leafWorkflowId
        with(workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "poll and complete the 2 tasks in the leaf workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the new leaf workflow reached COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "verify that the mid level and root workflows reach COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            (!tasks[1].subworkflowChanged) // flag is reset after decide
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed with resume flag on the mid-level workflow.
     *
     * Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent (root) workflows are also retried.
     * When the leaf workflow completes successfully, both the mid-level and root workflows also complete successfully.
     */
    def "Test retry on the mid-level with resume flag in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the root workflow"
        workflowExecutor.retry(midLevelWorkflowId, true)

        then: "verify that the sub workflow task in root workflow is updated"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the sub workflow task in mid level workflow is updated"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the previously failed task in leaf workflow is in SCHEDULED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "the mid level workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            !tasks[1].subworkflowChanged // flag is reset after "decide"
        }

        and: "the root workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            !tasks[1].subworkflowChanged // flag is reset after "decide"
        }

        when: "poll and complete the previously failed integration_task_2 task"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the leaf workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "the mid level workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            !tasks[1].subworkflowChanged // flag is reset after decide
        }

        and: "the root workflow is in COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            !tasks[1].subworkflowChanged // flag is reset after decide
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed on the leaf workflow.
     *
     * Expectation: The leaf workflow resumes its FAILED task and updates both its parent (mid-level) and grandparent (root).
     * When the leaf workflow completes successfully, both the mid-level and root workflows also complete successfully.
     */
    def "Test retry on the leaf in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the leaf workflow"
        workflowExecutor.retry(leafWorkflowId, false)

        then: "verify that the leaf workflow is in RUNNING state and failed task is retried"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        then: "verify that the mid-level workflow is updated"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the root workflow' is updated"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        when: "poll and complete the scheduled task in the leaf workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the leaf workflow reached COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "verify that the mid level and root workflows reach COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            (!tasks[1].subworkflowChanged) // flag is reset after decide
        }
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            (!tasks[1].subworkflowChanged) // flag is reset after decide
        }
        //endregion
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task
     * in the leaf workflow.
     *
     * A retry is executed with resume flag on the leaf workflow.
     *
     * Expectation: The leaf workflow resumes its FAILED task and updates both its parent (mid-level) and grandparent (root).
     * When the leaf workflow completes successfully, both the mid-level and root workflows also complete successfully.
     */
    def "Test retry on the leaf with resume flag in a 3-level subworkflow"() {
        //region Test case
        when: "do a retry on the leaf workflow"
        workflowExecutor.retry(leafWorkflowId, true)

        then: "verify that the leaf workflow is in RUNNING state and failed task is retried"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        then: "verify that the mid-level workflow is updated"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        and: "verify that the root workflow is updated"
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].subworkflowChanged
        }

        when: "poll and complete the scheduled task in the leaf workflow"
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the leaf workflow reached COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(leafWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[1].retried
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].retriedTaskId == tasks[1].taskId
        }

        when: "the mid level and root workflows are 'decided'"
        sweep(midLevelWorkflowId)
        sweep(rootWorkflowId)

        then: "verify that the mid level and root workflows reach COMPLETED state"
        with(workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            (!tasks[1].subworkflowChanged) // flag is reset after decide
        }
        with(workflowExecutionService.getExecutionStatus(rootWorkflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == TASK_TYPE_SUB_WORKFLOW
            tasks[1].status == Task.Status.COMPLETED
            (!tasks[1].subworkflowChanged) // flag is reset after decide
        }
        //endregion
    }
}
