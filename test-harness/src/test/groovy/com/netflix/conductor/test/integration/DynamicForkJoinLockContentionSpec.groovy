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
import com.netflix.conductor.service.ExecutionLockService
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared
import spock.util.concurrent.PollingConditions

/**
 * End-to-end reproduction of the multi-minute pause seen in dynamic FORK/JOIN workflows under
 * workflow-lock contention (reported on Conductor 3.30.2 with Redis queue + distributed locks).
 *
 * Mechanism (all real production paths; only the lock implementation differs):
 *   1. A JOIN completing runs through {@link com.netflix.conductor.core.execution.AsyncSystemTaskExecutor},
 *      which on completion calls {@code workflowExecutor.decide(workflowId)} to schedule the next task.
 *   2. {@code decide(workflowId)} must acquire the workflow lock. On a lock miss it returns null; the
 *      completion-event callers ignore that null, so the next task is never scheduled and the workflow
 *      parks on its decider-queue entry — which a polled task postpones out to responseTimeoutSeconds
 *      (e.g. 600s), i.e. the multi-minute pause.
 *   3. The fix: {@code decide(String)} re-queues the workflow to the decider queue with a short
 *      (lockTimeToTry-scale) backoff on a lock miss, so the sweeper re-evaluates it within seconds
 *      once the lock frees, instead of waiting out responseTimeoutSeconds.
 *
 * We create real lock contention by holding the workflow lock from a separate thread (LocalOnlyLock
 * is a per-id reentrant lock, so contention must come from another thread). The harness runs the real
 * background sweeper (sweeperThreadCount=1) against the real Redis-backed decider queue, so recovery
 * here is driven by production machinery, not a manual sweep. This test does NOT force recovery — it
 * asserts the workflow recovers on its own within seconds of the lock releasing, which holds only when
 * {@code decide()} re-queues on the lock miss.
 */
@TestPropertySource(properties = [
        "conductor.app.workflow-execution-lock-enabled=true",
        "conductor.app.lockLeaseTime=60000ms",
        "conductor.app.lockTimeToTry=1000ms"
])
class DynamicForkJoinLockContentionSpec extends AbstractSpecification {

    @Autowired
    Join joinTask

    @Autowired
    ExecutionLockService executionLockService

    @Shared
    def DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest"

    def setup() {
        workflowTestUtil.registerWorkflows('dynamic_fork_join_integration_test.json',
                'simple_workflow_3_integration_test.json')
    }

    def "a JOIN completing under a contended workflow lock recovers within seconds instead of parking"() {
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

        when: "another actor holds the workflow lock (e.g. an in-flight decide / unreleased lease)"
        def lockAcquired = new CountDownLatch(1)
        def releaseLock = new CountDownLatch(1)
        def lockReleased = new CountDownLatch(1)
        // LocalOnlyLock is a per-id reentrant lock: acquire AND release on the same foreign thread so
        // it actually contends with decide()/sweep() running on other threads.
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

        then: "the JOIN itself completes (execute() needs no workflow lock) ..."
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            getTaskByRefName("dynamicfanouttask_join").status == Task.Status.COMPLETED
            // ... but the post-completion decide() could not acquire the lock, so the next task
            // (integration_task_4) is NOT scheduled while the lock is held: the workflow is stalled.
            tasks.every { it.taskType != 'integration_task_4' }
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "the lock is released"
        releaseLock.countDown()
        assert lockReleased.await(5, TimeUnit.SECONDS)

        then: "the workflow recovers on its own within seconds — no manual sweep"
        // decide() re-queued the workflow on the lock miss, so the background sweeper re-evaluates it
        // promptly once the lock is free and schedules integration_task_4. WITHOUT the decide() fix the
        // decider entry sits at responseTimeoutSeconds (>= 120s here), so this times out.
        new PollingConditions(timeout: 20, initialDelay: 0.2, delay: 0.2).eventually {
            def next = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
                    .tasks.find { it.taskType == 'integration_task_4' }
            assert next != null
            assert next.status == Task.Status.SCHEDULED
        }

        and: "the workflow can run to completion once unblocked"
        workflowTestUtil.pollAndCompleteTask('integration_task_4', 'task4.worker')
        new PollingConditions(timeout: 10).eventually {
            assert workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
                    .status == Workflow.WorkflowStatus.COMPLETED
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
