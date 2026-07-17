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
 * <p>The fix: the adapter follows the system-task status contract. {@code start()} persists the
 * standard SCHEDULED → IN_PROGRESS transition BEFORE invoking the blocking worker method; a
 * redelivered queue message reaches a second system-task-worker as {@code execute()} on an
 * IN_PROGRESS task with no callback due, which does nothing — the operation runs exactly once.
 *
 * <p>This spec drives the REAL production path: the worker bean is registered through the real
 * WorkerTaskAnnotationScanner, the adapter comes out of the real SystemTaskRegistry, and execution
 * goes through the real AsyncSystemTaskExecutor against the real queue.
 *
 * <p>E2E assertion: a workflow containing an annotated async system task whose execution outlasts
 * the unack/redelivery window must invoke the underlying operation EXACTLY ONCE.
 *
 * <h3>What each step simulates from the real incident</h3>
 * <ul>
 *   <li>The blocking worker method = the long-running provider call (e.g. the agent-loop LLM turn)
 *       that outlasts the queue's unack window.</li>
 *   <li>{@code setUnackTimeout(queue, taskId, 1000)} after the first pop = compresses the real
 *       ~30-60s unack window down to ~1s so the test is deterministic and fast; it represents the
 *       visibility window that is about to elapse. The message IS redelivered, but the second
 *       execution sees the persisted IN_PROGRESS status and does nothing.</li>
 *   <li>The second {@code pop} after the compressed window = the queue's unack sweep re-making the
 *       still-popped message available, and a second system-task worker polling it (the exact
 *       production redelivery path). Whether it succeeds is gated by the real queue visibility.</li>
 *   <li>The second {@code execute()} = the second worker invoking the same blocking method again
 *       while the task is still SCHEDULED in the store = the duplicate provider call.</li>
 * </ul>
 */
class Issue1321DuplicateAsyncSystemTaskSpec extends AbstractSpecification {

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
        if (workflowTestUtil.getPersistedTaskDefinition('controllable_task').isEmpty()) {
            TaskDef taskDef = new TaskDef()
            taskDef.name = 'controllable_task'
            taskDef.ownerEmail = 'test@harness.com'
            taskDef.responseTimeoutSeconds = RESPONSE_TIMEOUT_SECONDS
            taskDef.timeoutSeconds = 3600
            taskDef.retryCount = 0
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

    def "annotated async system task that outlasts the unack window must execute exactly once"() {
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
        // Compress the real unack/visibility window (~30-60s in production) to ~1s so redelivery
        // becomes observable within the test's time budget. The message WILL be redelivered; the
        // redelivered execution sees the persisted IN_PROGRESS status and does nothing.
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) })
        worker1.setDaemon(true)
        worker1.start()

        and: "the worker method has been entered and is blocking (IN_PROGRESS already persisted)"
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)

        and: "the compressed unack window elapses"
        Thread.sleep(1500)

        and: "a second system-task worker polls the queue (simulating the unack sweep redelivery)"
        List<String> polled2 = popWithRetry(3000)
        if (!polled2.isEmpty()) {
            // Pre-fix: the message was redelivered; the second worker invokes the method again.
            asyncSystemTaskExecutor.execute(adapter, polled2[0])
        }

        then: "the underlying operation was invoked EXACTLY ONCE"
        controllableWorker.invocations.get() == 1

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }
}
