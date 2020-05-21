/*
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
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.common.utils.TaskUtils
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Specification

import javax.inject.Inject

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

@ModulesForTesting([TestModule.class])
class DoWhileSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    MetadataService metadataService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowTestUtil workflowTestUtil

    @Inject
    QueueDAO queueDAO

    def setup() {
        workflowTestUtil.registerWorkflows("do_while_integration_test.json",
                "do_while_multiple_integration_test.json")
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
    }

    def "Test workflow with a single iteration Do While task"() {
        given: "Number of iterations of the loop is set to 1"
        def workflowInput = new HashMap()
        workflowInput['loop'] = 1

        when: "A do_while workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow("Do_While_Workflow", 1, "looptest", workflowInput, null, null)

        then: "Verify that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Polling and completing first task"
        Tuple polledAndCompletedTask0 = workflowTestUtil.pollAndCompleteTask('integration_task_0', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0)
        verifyTaskIteration(polledAndCompletedTask0[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing second task"
        Tuple polledAndCompletedTask1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1)
        verifyTaskIteration(polledAndCompletedTask1[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing third task"
        Tuple polledAndCompletedTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in completed state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2)
        verifyTaskIteration(polledAndCompletedTask2[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 6
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
        }
    }

    def "Test workflow with multiple Do While tasks with multiple iterations"() {
        given: "Number of iterations of the first loop is set to 2 and second loop is set to 1"
        def workflowInput = new HashMap()
        workflowInput['loop'] = 2
        workflowInput['loop2'] = 1

        when: "A workflow with multiple do while tasks with multiple iterations is started"
        def workflowInstanceId = workflowExecutor.startWorkflow("Do_While_Multiple", 1, "looptest", workflowInput, null, null)

        then: "Verify that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Polling and completing first task"
        Tuple polledAndCompletedTask0 = workflowTestUtil.pollAndCompleteTask('integration_task_0', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0)
        verifyTaskIteration(polledAndCompletedTask0[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.SCHEDULED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing second task"
        Tuple polledAndCompletedTask1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1)
        verifyTaskIteration(polledAndCompletedTask1[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 6
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing third task"
        Tuple polledAndCompletedTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2)
        verifyTaskIteration(polledAndCompletedTask2[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0'
            tasks[6].status == Task.Status.SCHEDULED
        }

        when: "Polling and completing second iteration of first task"
        Tuple polledAndCompletedSecondIterationTask0 = workflowTestUtil.pollAndCompleteTask('integration_task_0', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask0, [:])
        verifyTaskIteration(polledAndCompletedSecondIterationTask0[0] as Task, 2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 11
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0'
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == 'FORK'
            tasks[7].status == Task.Status.COMPLETED
            tasks[8].taskType == 'integration_task_1'
            tasks[8].status == Task.Status.SCHEDULED
            tasks[9].taskType == 'integration_task_2'
            tasks[9].status == Task.Status.SCHEDULED
            tasks[10].taskType == 'JOIN'
            tasks[10].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing second iteration of second task"
        Tuple polledAndCompletedSecondIterationTask1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask1)
        verifyTaskIteration(polledAndCompletedSecondIterationTask1[0] as Task, 2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 11
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0'
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == 'FORK'
            tasks[7].status == Task.Status.COMPLETED
            tasks[8].taskType == 'integration_task_1'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'integration_task_2'
            tasks[9].status == Task.Status.SCHEDULED
            tasks[10].taskType == 'JOIN'
            tasks[10].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing second iteration of third task"
        Tuple polledAndCompletedSecondIterationTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedSecondIterationTask2)
        verifyTaskIteration(polledAndCompletedSecondIterationTask2[0] as Task, 2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 13
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0'
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == 'FORK'
            tasks[7].status == Task.Status.COMPLETED
            tasks[8].taskType == 'integration_task_1'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'integration_task_2'
            tasks[9].status == Task.Status.COMPLETED
            tasks[10].taskType == 'JOIN'
            tasks[10].status == Task.Status.COMPLETED
            tasks[11].taskType == 'DO_WHILE'
            tasks[11].status == Task.Status.IN_PROGRESS
            tasks[12].taskType == 'integration_task_3'
            tasks[12].status == Task.Status.SCHEDULED
        }

        when: "Polling and completing task within the second do while"
        Tuple polledAndCompletedIntegrationTask3 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in completed state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedIntegrationTask3)
        verifyTaskIteration(polledAndCompletedIntegrationTask3[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 13
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'FORK'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'integration_task_1'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_2'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'JOIN'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'integration_task_0'
            tasks[6].status == Task.Status.COMPLETED
            tasks[7].taskType == 'FORK'
            tasks[7].status == Task.Status.COMPLETED
            tasks[8].taskType == 'integration_task_1'
            tasks[8].status == Task.Status.COMPLETED
            tasks[9].taskType == 'integration_task_2'
            tasks[9].status == Task.Status.COMPLETED
            tasks[10].taskType == 'JOIN'
            tasks[10].status == Task.Status.COMPLETED
            tasks[11].taskType == 'DO_WHILE'
            tasks[11].status == Task.Status.COMPLETED
            tasks[12].taskType == 'integration_task_3'
            tasks[12].status == Task.Status.COMPLETED
        }
    }

    def "Test retrying a failed do while workflow"() {
        setup: "Update the task definition with no retries"
        def taskName = 'integration_task_0'
        def persistedTaskDefinition = workflowTestUtil.getPersistedTaskDefinition(taskName).get()
        def modifiedTaskDefinition = new TaskDef(persistedTaskDefinition.name, persistedTaskDefinition.description,
                0, persistedTaskDefinition.timeoutSeconds)
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "A do while workflow is started"
        def workflowInput = new HashMap()
        workflowInput['loop'] = 1
        def workflowInstanceId = workflowExecutor.startWorkflow("Do_While_Workflow", 1, "looptest", workflowInput, null, null)

        then: "Verify that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Polling and failing first task"
        Tuple polledAndFailedTask0 = workflowTestUtil.pollAndFailTask('integration_task_0', 'integration.test.worker', "induced..failure")

        then: "Verify that the task was polled and acknowledged and workflow is in failed state"
        verifyPolledAndAcknowledgedTask(polledAndFailedTask0)
        verifyTaskIteration(polledAndFailedTask0[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.CANCELED
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.FAILED
        }

        when: "The workflow is retried"
        workflowExecutor.retry(workflowInstanceId)

        then: "Verify that workflow is running"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "Polling and completing first task"
        Tuple polledAndCompletedTask0 = workflowTestUtil.pollAndCompleteTask('integration_task_0', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask0)
        verifyTaskIteration(polledAndCompletedTask0[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'FORK'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_1'
            tasks[4].status == Task.Status.SCHEDULED
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing second task"
        Tuple polledAndCompletedTask1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in running state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1)
        verifyTaskIteration(polledAndCompletedTask1[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 7
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.IN_PROGRESS
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'FORK'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_1'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.SCHEDULED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.IN_PROGRESS
        }

        when: "Polling and completing third task"
        Tuple polledAndCompletedTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'integration.test.worker')

        then: "Verify that the task was polled and acknowledged and workflow is in completed state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2)
        verifyTaskIteration(polledAndCompletedTask2[0] as Task, 1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 7
            tasks[0].taskType == 'DO_WHILE'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_0'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_0'
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].taskType == 'FORK'
            tasks[3].status == Task.Status.COMPLETED
            tasks[4].taskType == 'integration_task_1'
            tasks[4].status == Task.Status.COMPLETED
            tasks[5].taskType == 'integration_task_2'
            tasks[5].status == Task.Status.COMPLETED
            tasks[6].taskType == 'JOIN'
            tasks[6].status == Task.Status.COMPLETED
        }

        cleanup: "Reset the task definition"
        metadataService.updateTaskDef(persistedTaskDefinition)
    }

    void verifyTaskIteration(Task task, int iteration) {
        assert task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration()))
        assert task.iteration == iteration
    }
}
