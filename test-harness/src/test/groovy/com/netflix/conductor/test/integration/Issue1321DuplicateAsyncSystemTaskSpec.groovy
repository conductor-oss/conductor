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
 * <p>The fix: before invoking, the adapter reserves the task's own queue message — postponing it
 * out to responseTimeout via WorkflowExecutor.updateTask — so it stays present and invisible for
 * the duration of the call and is not redelivered mid-invocation.
 *
 * <p>Drives the real path (WorkerTaskAnnotationScanner, SystemTaskRegistry, AsyncSystemTaskExecutor,
 * real queue) and asserts the underlying operation is invoked EXACTLY ONCE.
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

        when: "worker #1 pops and executes (reserve + invoke); the method blocks"
        def taskId = startedWf.tasks[0].taskId
        List<String> polled1 = popWithRetry(3000)
        assert polled1 == [taskId]
        // compress the visibility window; the fix's reserve (postpone to responseTimeout) overrides it
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, taskId) })
        worker1.setDaemon(true)
        worker1.start()
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)

        and: "the compressed window elapses and a second worker polls (redelivery attempt)"
        Thread.sleep(1500)
        List<String> polled2 = popWithRetry(3000)
        if (!polled2.isEmpty()) {
            asyncSystemTaskExecutor.execute(adapter, polled2[0]) // pre-fix: duplicate invocation
        }

        then: "the underlying operation was invoked EXACTLY ONCE"
        controllableWorker.invocations.get() == 1

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }
}
