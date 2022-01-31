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
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.UserTask

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class TaskLimitsWorkflowSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    UserTask userTask

    def RATE_LIMITED_SYSTEM_TASK_WORKFLOW = 'test_rate_limit_system_task_workflow'
    def RATE_LIMITED_SIMPLE_TASK_WORKFLOW = 'test_rate_limit_simple_task_workflow'
    def CONCURRENCY_EXECUTION_LIMITED_WORKFLOW = 'test_concurrency_limits_workflow'

    def setup() {
        workflowTestUtil.registerWorkflows(
                'rate_limited_system_task_workflow_integration_test.json',
                'rate_limited_simple_task_workflow_integration_test.json',
                'concurrency_limited_task_workflow_integration_test.json'
        )
    }

    def "Verify that the rate limiting for system tasks is honored"() {
        when: "Start a workflow that has a rate limited system task in it"
        def workflowInstanceId = workflowExecutor.startWorkflow(RATE_LIMITED_SYSTEM_TASK_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'USER_TASK'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Execute the user task"
        def scheduledTask1 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[0]
        asyncSystemTaskExecutor.execute(userTask, scheduledTask1.taskId)

        then: "Verify the state of the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'USER_TASK'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "A new instance of the workflow is started"
        def workflowTwoInstanceId = workflowExecutor.startWorkflow(RATE_LIMITED_SYSTEM_TASK_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'USER_TASK'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Execute the user task on the second workflow"
        def scheduledTask2 = workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true).tasks[0]
        asyncSystemTaskExecutor.execute(userTask, scheduledTask2.taskId)

        then: "Verify the state of the workflow is still in running state"
        with(workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'USER_TASK'
            tasks[0].status == Task.Status.SCHEDULED
        }
    }

    def "Verify that the rate limiting for simple tasks is honored"() {
        when: "Start a workflow that has a rate limited simple task in it"
        def workflowInstanceId = workflowExecutor.startWorkflow(RATE_LIMITED_SIMPLE_TASK_WORKFLOW, 1, '', [:], null,
                null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'test_simple_task_with_rateLimits'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "polling and completing the task"
        Tuple polledAndCompletedTask = workflowTestUtil.pollAndCompleteTask('test_simple_task_with_rateLimits', 'rate.limit.test.worker')

        then: "verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask)

        and: "the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'test_simple_task_with_rateLimits'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "A new instance of the workflow is started"
        def workflowTwoInstanceId = workflowExecutor.startWorkflow(RATE_LIMITED_SIMPLE_TASK_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'test_simple_task_with_rateLimits'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "polling for the task"
        def polledTask = workflowExecutionService.poll('test_simple_task_with_rateLimits', 'rate.limit.test.worker')

        then: "verify that no task is returned"
        !polledTask

        when: "sleep for 10 seconds to ensure rate limit duration is past"
        Thread.sleep(10000L)

        and: "the task offset time is reset to ensure that a task is returned on the next poll"
        queueDAO.resetOffsetTime('test_simple_task_with_rateLimits',
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true).tasks[0].taskId)

        and: "polling and completing the task"
        polledAndCompletedTask = workflowTestUtil.pollAndCompleteTask('test_simple_task_with_rateLimits', 'rate.limit.test.worker')

        then: "verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask)

        and: "the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'test_simple_task_with_rateLimits'
            tasks[0].status == Task.Status.COMPLETED
        }
    }

    def "Verify that concurrency limited tasks are honored during workflow execution"() {
        when: "Start a workflow that has a concurrency execution limited task in it"
        def workflowInstanceId = workflowExecutor.startWorkflow(CONCURRENCY_EXECUTION_LIMITED_WORKFLOW, 1,
                '', [:], null, null, null)


        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'test_task_with_concurrency_limit'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The task is polled and acknowledged"
        def polledTask1 = workflowExecutionService.poll('test_task_with_concurrency_limit', 'test_task_worker')
        def ackPolledTask1 = workflowExecutionService.ackTaskReceived(polledTask1.taskId)

        then: "Verify that the task was polled and acknowledged"
        polledTask1.taskType == 'test_task_with_concurrency_limit'
        polledTask1.workflowInstanceId == workflowInstanceId
        ackPolledTask1

        when: "A additional workflow that has a concurrency execution limited task in it"
        def workflowTwoInstanceId = workflowExecutor.startWorkflow(CONCURRENCY_EXECUTION_LIMITED_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'test_task_with_concurrency_limit'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The task is polled"
        def polledTaskTry1 = workflowExecutionService.poll('test_task_with_concurrency_limit', 'test_task_worker')

        then: "Verify that there is no task returned"
        !polledTaskTry1

        when: "The task that was polled and acknowledged is completed"
        polledTask1.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(new TaskResult(polledTask1))

        and: "The task offset time is reset to ensure that a task is returned on the next poll"
        queueDAO.resetOffsetTime('test_task_with_concurrency_limit',
                workflowExecutionService.getExecutionStatus(workflowTwoInstanceId, true).tasks[0].taskId)

        then: "Verify that the first workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'test_task_with_concurrency_limit'
            tasks[0].status == Task.Status.COMPLETED
        }

        and: "The task is polled again and acknowledged"
        def polledTaskTry2 = workflowExecutionService.poll('test_task_with_concurrency_limit', 'test_task_worker')
        def ackPolledTaskTry2 = workflowExecutionService.ackTaskReceived(polledTaskTry2.taskId)

        then: "Verify that the task is returned since there are no tasks in progress"
        polledTaskTry2.taskType == 'test_task_with_concurrency_limit'
        polledTaskTry2.workflowInstanceId == workflowTwoInstanceId
        ackPolledTaskTry2
    }
}
