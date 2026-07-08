/*
 * Copyright 2024 Conductor Authors.
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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.Join
import com.netflix.conductor.core.utils.Utils
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionLockService
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

/**
 * End-to-end reproduction of the RegressWorkflow ~10-minute pause (reported on Conductor 3.30.2 with
 * Spanner persistence and Redis queue + locks) and validation of the sweeper fix.
 *
 * Mechanism (all real production paths; only the lock implementation differs from the customer's):
 *   1. A JOIN completing runs through {@link com.netflix.conductor.core.execution.AsyncSystemTaskExecutor},
 *      which on completion calls {@code workflowExecutor.decide(workflowId)} to schedule the next task.
 *   2. {@code decide(workflowId)} must acquire the workflow lock; when it is contended it returns null,
 *      so the post-JOIN task is never scheduled and the workflow parks.
 *   3. Before the fix, the sweeper bare-returned on the same lock miss with NO re-queue, so the workflow
 *      stayed parked on its decider-queue entry — which a polled task postpones out to
 *      responseTimeoutSeconds (600s for the customer) — surfacing as the ~10-minute pause.
 *   4. The fix re-queues the workflow to the decider queue with a bounded backoff (lockLeaseTime/2) on a
 *      lock miss, so it is retried in seconds instead of parking.
 *
 * We create real lock contention by holding the workflow lock from a separate thread. The harness uses
 * {@code LocalOnlyLock} (Redisson is not on the test classpath); the decide()/sweep() lock-miss branches
 * exercised here are the real ones, only the Lock implementation differs. The decider queue, however, is
 * the real Redis-backed queue the harness already provisions via Testcontainers, so the re-queue
 * (ZADD) and {@code containsMessage} assertions run against real Redis.
 *
 * Determinism: the fix re-queues with a bounded backoff of {@code lockLeaseTime/2} (30s here), so the
 * re-pushed decider message is not "due" for 30s. The background sweeper's {@code pop} only returns
 * due messages, so it cannot pop/move the message during the sub-second {@code containsMessage} check —
 * the assertion is stable even with the sweeper thread running. (sweeperThreadCount must be &gt; 0, so the
 * background loop cannot be disabled outright.)
 */
@TestPropertySource(properties = [
        // Enable the workflow execution lock and use the customer's lock timings. lockLeaseTime drives
        // the sweeper's re-queue backoff (lockLeaseTime/2 = 30s), which keeps the assertion deterministic.
        "conductor.app.workflow-execution-lock-enabled=true",
        "conductor.app.lockLeaseTime=60000ms",
        "conductor.app.lockTimeToTry=1000ms"
])
class DynamicForkJoinLockContentionSpec extends AbstractSpecification {

    @Autowired
    Join joinTask

    @Autowired
    ExecutionLockService executionLockService

    @Autowired
    QueueDAO queueDAO

    @Shared
    def DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest"

    def setup() {
        workflowTestUtil.registerWorkflows('dynamic_fork_join_integration_test.json',
                'simple_workflow_3_integration_test.json')
    }

    def "sweeper re-queues a lock-contended workflow instead of leaving it parked at the response timeout"() {
        given: "a dynamic fork/join workflow driven to the point where the JOIN is ready to complete"
        def workflowInstanceId = startWorkflow(DYNAMIC_FORK_JOIN_WF, 1, 'dynamic_fork_join_workflow', [:], null)

        WorkflowTask workflowTask2 = new WorkflowTask(name: 'integration_task_2', taskReferenceName: 'xdt1')
        WorkflowTask workflowTask3 = new WorkflowTask(name: 'integration_task_3', taskReferenceName: 'xdt2')
        def dynamicTasksInput = ['xdt1': ['k1': 'v1'], 'xdt2': ['k2': 'v2']]

        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker',
                ['dynamicTasks': [workflowTask2, workflowTask3], 'dynamicTasksInput': dynamicTasksInput])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker', ['ok1': 'ov1'])
        workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.worker', ['ok1': 'ov1'])
        sweep(workflowInstanceId)

        def joinTaskId = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
                .getTaskByRefName("dynamicfanouttask_join").taskId

        when: "another actor holds the workflow lock (e.g. an in-flight decide / unreleased lock lease)"
        def lockAcquired = new CountDownLatch(1)
        def releaseLock = new CountDownLatch(1)
        def lockReleased = new CountDownLatch(1)
        // LocalOnlyLock is a per-id ReentrantLock: it must be acquired AND released on the same foreign
        // thread to actually contend with decide()/sweep() running on the test thread.
        def holder = new Thread({
            executionLockService.acquireLock(workflowInstanceId)
            lockAcquired.countDown()
            releaseLock.await()
            executionLockService.releaseLock(workflowInstanceId)
            lockReleased.countDown()
        }, "lock-holder")
        holder.daemon = true
        holder.start()
        assert lockAcquired.await(5, TimeUnit.SECONDS)

        and: "the JOIN is executed while the lock is held"
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId)

        then: "the JOIN itself completes (execute() does not need the workflow lock) ..."
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            getTaskByRefName("dynamicfanouttask_join").status == Task.Status.COMPLETED
            // ... but the post-completion decide() could not acquire the lock, so the next task
            // (integration_task_4) is NEVER scheduled: the workflow is stalled at the JOIN boundary.
            tasks.every { it.taskType != 'integration_task_4' }
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "the decider-queue entry is cleared so the re-queue assertion is unambiguous"
        queueDAO.remove(Utils.DECIDER_QUEUE, workflowInstanceId)

        and: "the sweeper runs while the lock is still held"
        sweep(workflowInstanceId)

        then: "the fix re-queues the workflow for a prompt retry (pre-fix: bare return, no re-queue -> false)"
        queueDAO.containsMessage(Utils.DECIDER_QUEUE, workflowInstanceId)

        and: "it is still stalled while the lock is held - the re-queue only schedules a near-term retry"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            tasks.every { it.taskType != 'integration_task_4' }
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "the lock is released and the workflow is swept again"
        releaseLock.countDown()
        assert lockReleased.await(5, TimeUnit.SECONDS)
        sweep(workflowInstanceId)

        then: "the workflow finally progresses and integration_task_4 is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            def next = tasks.find { it.taskType == 'integration_task_4' }
            next != null
            next.status == Task.Status.SCHEDULED
        }

        and: "the workflow can run to completion once unblocked"
        workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4.worker')
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
        }
    }

    def "control: with no lock contention the same JOIN schedules the next task immediately"() {
        given: "the same dynamic fork/join workflow driven to the JOIN-ready point"
        def workflowInstanceId = startWorkflow(DYNAMIC_FORK_JOIN_WF, 1, 'dynamic_fork_join_workflow', [:], null)

        WorkflowTask workflowTask2 = new WorkflowTask(name: 'integration_task_2', taskReferenceName: 'xdt1')
        WorkflowTask workflowTask3 = new WorkflowTask(name: 'integration_task_3', taskReferenceName: 'xdt2')
        def dynamicTasksInput = ['xdt1': ['k1': 'v1'], 'xdt2': ['k2': 'v2']]

        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker',
                ['dynamicTasks': [workflowTask2, workflowTask3], 'dynamicTasksInput': dynamicTasksInput])
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.worker', ['ok1': 'ov1'])
        workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.worker', ['ok1': 'ov1'])
        sweep(workflowInstanceId)

        def joinTaskId = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
                .getTaskByRefName("dynamicfanouttask_join").taskId

        when: "the JOIN is executed with the lock free"
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId)

        then: "the JOIN completes AND the next task is scheduled in the same decide - no stall"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            getTaskByRefName("dynamicfanouttask_join").status == Task.Status.COMPLETED
            def next = tasks.find { it.taskType == 'integration_task_4' }
            next != null
            next.status == Task.Status.SCHEDULED
        }
    }
}
