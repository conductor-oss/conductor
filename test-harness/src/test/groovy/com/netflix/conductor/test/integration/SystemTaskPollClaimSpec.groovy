/*
 * Copyright 2026 Conductor Authors.
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
import com.netflix.conductor.core.execution.tasks.SystemTaskWorker
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableWorker

/**
 * REGRESSION FOR ISSUES #1321 / #1322 driven through the batch-poll proposal: SystemTaskWorker
 * polls via {@code ExecutionService.poll} (which persists IN_PROGRESS at poll time, like a remote
 * worker) and {@code AnnotatedWorkflowSystemTask.execute} skips IN_PROGRESS tasks.
 *
 * <p>The behavioral contract asserted here is the fix's acceptance criteria and is
 * implementation-independent: a blocking annotated system task must (1) be invoked exactly once,
 * (2) read IN_PROGRESS while it runs, and (3) complete.
 */
class SystemTaskPollClaimSpec extends AbstractSpecification {

    @Autowired
    ControllableWorker controllableWorker

    @Autowired
    SystemTaskWorker systemTaskWorker

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
        registerTaskDef('controllable_task', RESPONSE_TIMEOUT_SECONDS)
        workflowTestUtil.registerWorkflows('controllable_async_system_task_workflow.json')
    }

    private void registerTaskDef(String name, int responseTimeoutSeconds) {
        if (workflowTestUtil.getPersistedTaskDefinition(name).isEmpty()) {
            TaskDef taskDef = new TaskDef()
            taskDef.name = name
            taskDef.ownerEmail = 'test@harness.com'
            taskDef.responseTimeoutSeconds = responseTimeoutSeconds
            taskDef.timeoutSeconds = 3600
            taskDef.retryCount = 0
            metadataService.registerTaskDef([taskDef])
        }
    }

    def "a blocking annotated task claimed by SystemTaskWorker must execute exactly once and complete"() {
        given: "the real adapter registered for the annotated worker by the annotation scanner"
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        assert adapter != null

        and: "the controllable worker is armed to block during its invocation"
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'batch_poll_' + UUID.randomUUID(), [:], null)
        def startedWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the async system task is SCHEDULED and queued"
        startedWf.status == Workflow.WorkflowStatus.RUNNING
        startedWf.tasks.size() == 1
        startedWf.tasks[0].taskType == QUEUE
        startedWf.tasks[0].status == Task.Status.SCHEDULED

        when: "the production system-task worker claims and dispatches the queue message"
        systemTaskWorker.start()
        systemTaskWorker.pollAndExecute(adapter, QUEUE)

        then: "the task is claimed before the blocking provider call begins"
        controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].status == Task.Status.IN_PROGRESS

        when: "the blocking call returns"
        controllableWorker.release.countDown()
        conditions.eventually {
            workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].status == Task.Status.COMPLETED
        }

        then: "the underlying operation was invoked EXACTLY ONCE and the task completed"
        controllableWorker.invocations.get() == 1

        cleanup:
        controllableWorker.release?.countDown()
    }
}
