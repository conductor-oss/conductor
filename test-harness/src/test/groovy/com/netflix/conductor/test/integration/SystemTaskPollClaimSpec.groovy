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
import com.netflix.conductor.core.execution.ClaimedSystemTask
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableWorker

/**
 * REGRESSION FOR ISSUES #1321 / #1322 under the poll-time-claim model.
 *
 * <p>The fix under test: {@code ExecutionService.claimSystemTasks} persists the SCHEDULED →
 * IN_PROGRESS transition and leases the queue message BEFORE the executor invokes the blocking
 * annotated method. A message redelivered while the lease is live hits the claim's in-flight guard
 * and is postponed without a claim — the blocking operation runs exactly once. Lease expiry doubles
 * as crash recovery: a task claimed by a node that died is re-claimed and re-executed once the
 * lease elapses.
 *
 * <p>This spec drives the REAL production path: the worker bean is registered through the real
 * WorkerTaskAnnotationScanner, the adapter (which declares {@code claimOnPoll()}) comes out of the
 * real async-system-task registry, claiming goes through the real ExecutionService against the real
 * queue, and execution through the real AsyncSystemTaskExecutor.
 */
class SystemTaskPollClaimSpec extends AbstractSpecification {

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
    static final String SHORT_LEASE_WF = 'controllable_short_lease_wf'
    static final String QUEUE = ControllableWorker.TASK_TYPE
    // Lease for the blocking test: must comfortably outlast the whole spec.
    static final int RESPONSE_TIMEOUT_SECONDS = 45
    // Lease for the crash-recovery test: short enough to expire within the test's budget.
    static final int SHORT_LEASE_SECONDS = 2

    def setup() {
        controllableWorker.reset()
        registerTaskDef('controllable_task', RESPONSE_TIMEOUT_SECONDS)
        registerTaskDef('controllable_task_short_lease', SHORT_LEASE_SECONDS)
        workflowTestUtil.registerWorkflows(
                'controllable_async_system_task_workflow.json',
                'controllable_short_lease_workflow.json')
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

    private List<ClaimedSystemTask> claimWithRetry(int maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            List<ClaimedSystemTask> claimed = workflowExecutionService.claimSystemTasks(QUEUE, 1, 300)
            if (claimed != null && !claimed.isEmpty()) {
                return claimed
            }
        }
        return []
    }

    def "a blocking annotated task is claimed IN_PROGRESS at poll time and a redelivered message cannot duplicate it"() {
        given: "the real adapter registered for the annotated worker by the annotation scanner"
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        assert adapter != null
        assert adapter.claimOnPoll()

        and: "the controllable worker is armed to block during its invocation"
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'poll_claim_' + UUID.randomUUID(), [:], null)
        def startedWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the async system task is SCHEDULED and queued"
        startedWf.status == Workflow.WorkflowStatus.RUNNING
        startedWf.tasks.size() == 1
        startedWf.tasks[0].taskType == QUEUE
        startedWf.tasks[0].status == Task.Status.SCHEDULED

        when: "system-task-worker #1 claims from the queue (nothing has executed yet)"
        def taskId = startedWf.tasks[0].taskId
        List<ClaimedSystemTask> claimed = claimWithRetry(3000)

        then: "the claim carries the pre-claim status and has ALREADY persisted IN_PROGRESS"
        claimed.size() == 1
        claimed[0].task().taskId == taskId
        claimed[0].preClaimStatus() == TaskModel.Status.SCHEDULED
        with(workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]) {
            status == Task.Status.IN_PROGRESS
            workerId != null && !workerId.isEmpty()
        }
        controllableWorker.invocations.get() == 0

        when: "worker #1 executes the claimed task (the method blocks, simulating the provider call)"
        Thread worker1 = new Thread({ asyncSystemTaskExecutor.execute(adapter, claimed[0]) })
        worker1.setDaemon(true)
        worker1.start()

        and: "the worker method has been entered and is blocking"
        assert controllableWorker.enteredRun.await(10, TimeUnit.SECONDS)

        and: "the leased message is forced visible early (simulating a repair-service re-add)"
        queueDAO.setUnackTimeout(QUEUE, taskId, 1000L)
        Thread.sleep(1500)

        and: "a second system-task worker attempts to claim the redelivered message"
        List<ClaimedSystemTask> claimed2 = workflowExecutionService.claimSystemTasks(QUEUE, 1, 300)

        then: "the in-flight guard refuses the claim: the lease is live, the invocation continues"
        claimed2.isEmpty()
        controllableWorker.invocations.get() == 1

        when: "the blocking call finally returns"
        controllableWorker.release.countDown()
        worker1.join(10_000)
        def finishedTask = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]

        then: "the underlying operation was invoked EXACTLY ONCE and the task completed"
        controllableWorker.invocations.get() == 1
        finishedTask.status == Task.Status.COMPLETED

        cleanup:
        controllableWorker.release?.countDown()
        worker1?.join(5000)
    }

    def "a claimed task whose node died is re-claimed and executed after the lease expires"() {
        given: "the real adapter for the annotated worker"
        WorkflowSystemTask adapter = asyncSystemTasks.find { it.taskType == QUEUE }
        assert adapter != null

        and: "the worker does not block (the crash happens before the invocation)"
        controllableWorker.enteredRun = null
        controllableWorker.release = null

        when: "the workflow is started and worker #1 claims the task"
        def workflowId = startWorkflow(SHORT_LEASE_WF, 1, 'lease_expiry_' + UUID.randomUUID(), [:], null)
        def taskId = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].taskId
        List<ClaimedSystemTask> claimed = claimWithRetry(3000)

        then: "the task is claimed IN_PROGRESS but never executed - the claiming node 'dies'"
        claimed.size() == 1
        claimed[0].task().taskId == taskId
        controllableWorker.invocations.get() == 0
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].status == Task.Status.IN_PROGRESS

        when: "the short lease expires and the message becomes visible again"
        Thread.sleep((SHORT_LEASE_SECONDS + 1) * 1000L)

        and: "another node claims the redelivered message"
        List<ClaimedSystemTask> reclaimed = claimWithRetry(3000)

        then: "the expired lease allows the re-claim, carrying the IN_PROGRESS pre-claim status"
        reclaimed.size() == 1
        reclaimed[0].task().taskId == taskId
        reclaimed[0].preClaimStatus() == TaskModel.Status.IN_PROGRESS

        when: "the recovering node executes the re-claimed task"
        asyncSystemTaskExecutor.execute(adapter, reclaimed[0])

        then: "the operation ran exactly once overall and the task completed"
        controllableWorker.invocations.get() == 1
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].status == Task.Status.COMPLETED
    }
}
