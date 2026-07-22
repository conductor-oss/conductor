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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableWorker

/**
 * REPRODUCES ISSUE #1321 - Async system tasks running longer than the queue unack window are
 * executed twice.
 *
 * <p>Verifies the persisted-claim extension to the queue-reservation fix from PR #1369. The popped
 * message remains unacked and is reserved for responseTimeout before {@code start()}, while the
 * task is atomically moved to IN_PROGRESS with an ownership token. These scenarios cover the
 * normal lease and both boundaries: an unexpectedly early delivery is rejected while the claim is
 * active, and a late result cannot overwrite the timeout established after claim expiry.
 *
 * <p>This spec drives the REAL production path: the worker bean is registered through the real
 * WorkerTaskAnnotationScanner, the adapter comes out of the real SystemTaskRegistry, and execution
 * goes through the real AsyncSystemTaskExecutor against the real queue.
 *
 * <p>The state and queue observations are deliberately logged with an {@code ISSUE1321} prefix so
 * the exact Redis redelivery sequence is retained in the Gradle XML test output.
 */
class Issue1321DuplicateAsyncSystemTaskSpec extends AbstractSpecification {

    private static final Logger LOGGER = LoggerFactory.getLogger(Issue1321DuplicateAsyncSystemTaskSpec)

    @Autowired
    QueueDAO queueDAO

    @Autowired
    ControllableWorker controllableWorker

    // The same collection SystemTaskWorkerCoordinator polls in production; the annotation
    // scanner adds the AnnotatedWorkflowSystemTask adapters to it.
    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    Set<WorkflowSystemTask> asyncSystemTasks

    static final String WF = 'controllable_async_system_task_wf'
    static final String QUEUE = ControllableWorker.TASK_TYPE
    // The fix extends visibility to responseTimeoutSeconds; must be >> the compressed test window.
    static final int RESPONSE_TIMEOUT_SECONDS = 45

    def setup() {
        controllableWorker.reset()
        registerControllableTaskDef()
        workflowTestUtil.registerWorkflows('controllable_async_system_task_workflow.json')
    }

    private void registerControllableTaskDef() {
        Optional<TaskDef> existing = workflowTestUtil.getPersistedTaskDefinition('controllable_task')
        TaskDef taskDef = existing.orElseGet {
            TaskDef created = new TaskDef()
            created.name = 'controllable_task'
            created.ownerEmail = 'test@harness.com'
            created.timeoutSeconds = 3600
            created.retryCount = 0
            return created
        }
        taskDef.responseTimeoutSeconds = RESPONSE_TIMEOUT_SECONDS
        if (existing.isPresent()) {
            metadataService.updateTaskDef(taskDef)
        } else {
            metadataService.registerTaskDef([taskDef])
        }
    }

    private List<String> popWithRetry(int maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            List<String> ids = queueDAO.pop(QUEUE, 1, 300)
            if (ids != null && !ids.isEmpty()) {
                return ids
            }
        }
        return []
    }

    private Task taskState(String workflowId, String phase) {
        Task task = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]
        LOGGER.info(
                "ISSUE1321 {} status={} startTime={} updateTime={} callback={} queueContains={} invocations={}",
                phase,
                task.status,
                task.startTime,
                task.updateTime,
                task.callbackAfterSeconds,
                queueDAO.containsMessage(QUEUE, task.taskId),
                controllableWorker.invocations.get())
        return task
    }

    def "reservation prevents ordinary unack redelivery while annotated start is blocked"() {
        given: "the real adapter registered for the annotated worker by the annotation scanner"
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        assert adapter != null

        and: "the controllable worker is armed to block during its invocation"
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'issue1321_' + UUID.randomUUID(), [:], null)
        def startedWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the async system task is SCHEDULED and queued"
        startedWf.status == Workflow.WorkflowStatus.RUNNING
        startedWf.tasks.size() == 1
        startedWf.tasks[0].taskType == QUEUE
        startedWf.tasks[0].status == Task.Status.SCHEDULED

        when: "system-task-worker #1 pops the message and begins executing (the method blocks)"
        def taskId = startedWf.tasks[0].taskId
        List<String> polled1 = popWithRetry(3000)
        assert polled1 == [taskId]
        // Start with a short visibility timeout. Miguel's executor must replace it with the task's
        // 45-second response timeout before invoking the worker.
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) })
        worker1.setDaemon(true)
        worker1.start()

        and: "the worker method has been entered and is blocking (IN_PROGRESS already persisted)"
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)
        Task inFlight = taskState(workflowId, 'ordinary/in-flight')

        then: "the persisted claim marks the task IN_PROGRESS and reserves its queue message"
        inFlight.status == Task.Status.IN_PROGRESS
        inFlight.startTime > 0
        queueDAO.containsMessage(QUEUE, taskId)

        and: "the real sweeper observes the reserved unacked message while the provider is running"
        sweep(workflowId)

        and: "the compressed unack window elapses"
        Thread.sleep(1500)

        and: "a second system-task worker cannot poll the reserved message"
        List<String> polled2 = popWithRetry(3000)

        then:
        polled2.isEmpty()
        controllableWorker.invocations.get() == 1

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

    def "an unexpectedly early visible message is rejected by the active claim"() {
        given:
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)
        def workflowId = startWorkflow(WF, 1, 'issue1321_early_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        assert popWithRetry(3000) == [taskId]

        when: "worker one starts and Miguel's executor reserves the message for 45 seconds"
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) }, 'issue1321-worker-1')
        worker1.setDaemon(true)
        worker1.start()
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)
        Task beforeRedelivery = taskState(workflowId, 'early/before-forced-visibility')

        and: "an external queue event shortens that reservation and the message becomes visible"
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread.sleep(1500)
        List<String> redelivered = popWithRetry(3000)
        LOGGER.info("ISSUE1321 early/redelivered ids={}", redelivered)
        if (!redelivered.isEmpty()) {
            asyncSystemTaskExecutor.execute(adapter, taskId)
        }

        then: "the persisted ownership claim prevents a second provider invocation"
        beforeRedelivery.status == Task.Status.IN_PROGRESS
        redelivered == [taskId]
        controllableWorker.invocations.get() == 1
        taskState(workflowId, 'early/after-rejected-delivery').status == Task.Status.IN_PROGRESS

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

    def "response-timeout redelivery rejects the original invocation's late write"() {
        given: "a response timeout shorter than the blocked invocation"
        TaskDef taskDef = workflowTestUtil.getPersistedTaskDefinition('controllable_task').get()
        taskDef.responseTimeoutSeconds = 2
        metadataService.updateTaskDef(taskDef)
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)
        def workflowId = startWorkflow(WF, 1, 'issue1321_timeout_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        assert popWithRetry(3000) == [taskId]

        when: "the original invocation blocks beyond responseTimeout"
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) }, 'issue1321-timeout-worker-1')
        worker1.setDaemon(true)
        worker1.start()
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)
        taskState(workflowId, 'timeout/in-flight')
        Thread.sleep(2500)

        and: "the naturally expired queue message is processed by another executor"
        List<String> redelivered = popWithRetry(5000)
        LOGGER.info("ISSUE1321 timeout/redelivered ids={}", redelivered)
        assert redelivered == [taskId]
        asyncSystemTaskExecutor.execute(adapter, taskId)
        Task timedOut = taskState(workflowId, 'timeout/after-redelivery')

        then: "the second executor does not invoke the provider and marks the original attempt timed out"
        controllableWorker.invocations.get() == 1
        timedOut.status == Task.Status.TIMED_OUT

        when: "the original provider call returns after its attempt was timed out"
        controllableWorker.release.countDown()
        worker1.join(5000)
        Task afterLateWrite = taskState(workflowId, 'timeout/after-original-return')

        then: "the stale executor cannot overwrite the terminal state under the same task id"
        afterLateWrite.status == Task.Status.TIMED_OUT

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

    def "sweeper repair delivery cannot re-enter a long-running provider invocation"() {
        given:
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)
        def workflowId = startWorkflow(WF, 1, 'issue1321_sweeper_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        assert popWithRetry(3000) == [taskId]

        when: "the provider remains in flight after persisting its claim"
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) }, 'issue1321-sweeper-worker')
        worker1.setDaemon(true)
        worker1.start()
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)
        assert taskState(workflowId, 'sweeper/in-flight').status == Task.Status.IN_PROGRESS

        and: "the queue message is lost and the real WorkflowSweeper repairs it"
        queueDAO.remove(QUEUE, taskId)
        assert !queueDAO.containsMessage(QUEUE, taskId)
        sweep(workflowId)
        List<String> repaired = popWithRetry(5000)
        LOGGER.info('ISSUE1321 sweeper/repaired ids={}', repaired)
        assert repaired == [taskId]

        and: "a system-task worker processes the repaired delivery"
        asyncSystemTaskExecutor.execute(adapter, taskId)

        then: "the active claim rejects the delivery without invoking the provider again"
        controllableWorker.invocations.get() == 1
        taskState(workflowId, 'sweeper/after-repaired-delivery').status == Task.Status.IN_PROGRESS

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

    def "a completed invocation releases its claim and permits the requested callback"() {
        given:
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        controllableWorker.firstCallbackAfterSeconds = 1
        def workflowId = startWorkflow(WF, 1, 'issue1321_callback_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        assert popWithRetry(3000) == [taskId]

        when: "the first provider invocation requests a callback"
        asyncSystemTaskExecutor.execute(adapter, taskId)
        Task waiting = taskState(workflowId, 'callback/waiting')

        then:
        waiting.status == Task.Status.IN_PROGRESS
        waiting.callbackAfterSeconds == 1
        controllableWorker.invocations.get() == 1

        when: "the callback becomes due and is delivered"
        List<String> callbackDelivery = popWithRetry(5000)
        assert callbackDelivery == [taskId]
        asyncSystemTaskExecutor.execute(adapter, taskId)
        Task completed = taskState(workflowId, 'callback/completed')

        then: "the callback is a new invocation rather than an active-claim redelivery"
        controllableWorker.invocations.get() == 2
        completed.status == Task.Status.COMPLETED
    }

    def "workflow termination fences a blocked provider's late completion"() {
        given:
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)
        def workflowId = startWorkflow(WF, 1, 'issue1321_terminate_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        assert popWithRetry(3000) == [taskId]
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) }, 'issue1321-terminate-worker')
        worker1.setDaemon(true)
        worker1.start()
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)

        when: "the workflow is terminated while the provider call remains blocked"
        workflowExecutor.terminateWorkflow(workflowId, 'integration test termination')
        Task canceled = taskState(workflowId, 'terminate/canceled')
        controllableWorker.release.countDown()
        worker1.join(5000)
        Task afterLateReturn = taskState(workflowId, 'terminate/after-provider-return')

        then: "the provider cannot overwrite the cancellation"
        canceled.status == Task.Status.CANCELED
        afterLateReturn.status == Task.Status.CANCELED
        controllableWorker.invocations.get() == 1

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

}
