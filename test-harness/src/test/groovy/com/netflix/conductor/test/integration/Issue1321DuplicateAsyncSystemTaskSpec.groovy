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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableSystemTask

/**
 * REPRODUCES ISSUE #1321 - Async system tasks running longer than the queue unack window are
 * executed twice.
 *
 * <p>Fix contract asserted here: before {@code systemTask.start()} on a SCHEDULED, non-asyncComplete
 * task, {@code AsyncSystemTaskExecutor} must call
 * {@code queueDAO.setUnackTimeout(queueName, taskId, 1000L * taskDef.responseTimeoutSeconds)} (when
 * responseTimeoutSeconds > 0), so that the in-flight message's visibility timeout is extended for
 * the duration of the long-running execution and the queue does NOT redeliver it to a second
 * system-task worker mid-execution.
 *
 * <p>E2E assertion: a workflow containing an async system task whose execution outlasts the
 * unack/redelivery window must invoke the underlying operation EXACTLY ONCE.
 *
 * <p>Status: FAILS on current code (the underlying operation runs twice) until the fix is applied.
 *
 * <h3>What each step simulates from the real incident</h3>
 * <ul>
 *   <li>The blocking {@code start()} = the long-running provider call (e.g. the agent-loop LLM turn)
 *       that outlasts the queue's unack window.</li>
 *   <li>{@code setUnackTimeout(queue, taskId, 1000)} after the first pop = compresses the real
 *       ~60s (redis default 30s) unack window down to ~1s so the test is deterministic and fast;
 *       it represents the visibility window that is about to elapse.</li>
 *   <li>The second {@code pop} after the compressed window = the queue's unack sweep re-making the
 *       still-popped message available, and a second system-task worker polling it (the exact
 *       production redelivery path). Whether it succeeds is gated by the real queue visibility,
 *       which the fix's {@code setUnackTimeout} extends.</li>
 *   <li>The second {@code execute()} = the second worker calling {@code systemTask.start()} again
 *       while the task is still SCHEDULED in the store = the duplicate provider call.</li>
 * </ul>
 */
class Issue1321DuplicateAsyncSystemTaskSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    ControllableSystemTask controllableSystemTask

    static final String WF = 'controllable_async_system_task_wf'
    static final String QUEUE = ControllableSystemTask.NAME
    // The fix extends visibility to responseTimeoutSeconds; must be >> the compressed test window.
    static final int RESPONSE_TIMEOUT_SECONDS = 45

    def setup() {
        controllableSystemTask.reset()
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

    def "async system task that outlasts the unack window must execute exactly once"() {
        given: "the controllable system task is armed to block during start()"
        controllableSystemTask.enteredStart = new CountDownLatch(1)
        controllableSystemTask.release = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'issue1321_' + UUID.randomUUID(), [:], null)
        def startedWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the async system task is SCHEDULED and queued"
        startedWf.status == Workflow.WorkflowStatus.RUNNING
        startedWf.tasks.size() == 1
        startedWf.tasks[0].taskType == QUEUE
        startedWf.tasks[0].status == Task.Status.SCHEDULED

        when: "system-task-worker #1 pops the message and begins executing (start() blocks)"
        def taskId = startedWf.tasks[0].taskId
        List<String> polled1 = popWithRetry(3000)
        assert polled1 == [taskId]
        // Compress the real unack/visibility window (~30-60s in production) to ~1s so redelivery
        // becomes observable within the test's time budget. The fix will OVERRIDE this with
        // 1000 * responseTimeoutSeconds inside execute(), before start(), keeping it hidden.
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(controllableSystemTask, taskId) })
        worker1.setDaemon(true)
        worker1.start()

        and: "start() has entered and is blocking (the fix, if present, already extended the unack timeout)"
        assert controllableSystemTask.enteredStart.await(10, TimeUnit.SECONDS)

        and: "the compressed unack window elapses"
        Thread.sleep(1500)

        and: "a second system-task worker polls the queue (simulating the unack sweep redelivery)"
        List<String> polled2 = popWithRetry(3000)
        if (!polled2.isEmpty()) {
            // Pre-fix: the message was redelivered; the second worker executes start() again.
            asyncSystemTaskExecutor.execute(controllableSystemTask, polled2[0])
        }

        then: "the underlying operation was invoked EXACTLY ONCE"
        controllableSystemTask.startInvocations.get() == 1

        cleanup:
        controllableSystemTask.release?.countDown()
        worker1?.join(5000)
    }
}
