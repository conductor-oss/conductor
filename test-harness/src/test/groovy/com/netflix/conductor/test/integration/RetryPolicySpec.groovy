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

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.core.exception.TerminateWorkflowException
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * End-to-end integration tests for retry policy features:
 * - maxRetryDelaySeconds: caps the computed backoff delay so it never exceeds a configured ceiling
 * - backoffJitterMs: adds a random millisecond offset in [0, maxJitterMs] to each retry delay,
 *   spreading retries to prevent thundering-herd storms
 */
class RetryPolicySpec extends AbstractSpecification {

    @Autowired
    ExecutionDAOFacade executionDAOFacade

    private static final String TASK_NAME = 'retry_policy_task'
    private static final String WORKFLOW_NAME = 'retry_policy_wf'

    def setup() {
        def wfTask = new WorkflowTask()
        wfTask.name = TASK_NAME
        wfTask.taskReferenceName = 'retry_task_ref'
        wfTask.type = TaskType.SIMPLE

        def wfDef = new WorkflowDef()
        wfDef.name = WORKFLOW_NAME
        wfDef.version = 1
        wfDef.tasks = [wfTask]
        wfDef.ownerEmail = 'test@example.com'
        wfDef.timeoutSeconds = 3600
        wfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(wfDef)
    }

    def cleanup() {
        try { metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1) } catch (ignored) {}
        try { metadataService.unregisterTaskDef(TASK_NAME) } catch (ignored) {}
    }

    /** Poll until the next SCHEDULED task is available to be polled (handles callbackAfter delays). */
    private Task pollUntilAvailable() {
        Task polled = null
        conditions.eventually {
            polled = workflowExecutionService.poll(TASK_NAME, 'test-worker')
            assert polled != null
        }
        return polled
    }

    private static TaskResult failedResult(Task task) {
        def r = new TaskResult(task)
        r.status = TaskResult.Status.FAILED
        return r
    }

    // -------------------------------------------------------------------------
    // maxRetryDelaySeconds
    // -------------------------------------------------------------------------

    def "Exponential backoff delay is capped by maxRetryDelaySeconds"() {
        given: "A task def with exponential backoff, base delay 2 s, cap 3 s"
        // retry 0: 2 * 2^0 = 2 s (below cap); retry 1: 2 * 2^1 = 4 s → capped to 3 s
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 5
        taskDef.retryDelaySeconds = 2
        taskDef.retryLogic = TaskDef.RetryLogic.EXPONENTIAL_BACKOFF
        taskDef.maxRetryDelaySeconds = 3
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when: "Start the workflow and fail the task (retry 0 — raw = 2 s, below cap)"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'retry-cap-exp', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "Rescheduled task (retry 0) has callbackAfterSeconds = 2 (below the 3 s cap)"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED }
            assert rescheduled != null
            assert rescheduled.callbackAfterSeconds == 2
        }

        when: "Fail the task again (retry 1 — raw = 4 s, above cap)"
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "Rescheduled task (retry 1) has callbackAfterSeconds = 3 (capped from 4 s)"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.findAll { it.status == Task.Status.SCHEDULED }.last()
            assert rescheduled != null
            assert rescheduled.callbackAfterSeconds == 3
        }
    }

    def "Linear backoff delay is capped by maxRetryDelaySeconds"() {
        given: "A task def with linear backoff (scale=2), base delay 2 s, cap 5 s"
        // retry 0: 2 * 2 * 1 = 4 s (below cap 5 s); retry 1: 2 * 2 * 2 = 8 s → capped to 5 s
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 5
        taskDef.retryDelaySeconds = 2
        taskDef.retryLogic = TaskDef.RetryLogic.LINEAR_BACKOFF
        taskDef.backoffScaleFactor = 2
        taskDef.maxRetryDelaySeconds = 5
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when: "Start the workflow and fail the task (retry 0 — raw = 4 s, below cap)"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'retry-cap-lin', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "Rescheduled task has callbackAfterSeconds = 4 (below cap)"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED }
            assert rescheduled?.callbackAfterSeconds == 4
        }

        when: "Fail the task again (retry 1 — raw = 8 s, above cap)"
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "Rescheduled task has callbackAfterSeconds = 5 (capped from 8 s)"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.findAll { it.status == Task.Status.SCHEDULED }.last()
            assert rescheduled?.callbackAfterSeconds == 5
        }
    }

    // -------------------------------------------------------------------------
    // backoffJitterMs
    // -------------------------------------------------------------------------

    def "backoffJitterMs adds a random offset in [0, maxJitterMs] to the retry delay"() {
        given: "A task def with fixed retry (60 s) and 5000 ms of jitter"
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 3
        taskDef.retryDelaySeconds = 60
        taskDef.retryLogic = TaskDef.RetryLogic.FIXED
        taskDef.backoffJitterMs = 5000
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when: "Start the workflow and fail the task once"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'jitter-test', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "callbackAfterSeconds stays at the base delay (60)"
        and: "callbackAfterMs on the TaskModel is in [60_000, 65_000] — base + up to maxJitterMs"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED }
            assert rescheduled != null
            assert rescheduled.callbackAfterSeconds == 60
            def taskModel = executionDAOFacade.getTaskModel(rescheduled.taskId)
            assert taskModel.callbackAfterMs >= 60_000
            assert taskModel.callbackAfterMs <= 65_000
        }
    }

    def "backoffJitterMs of zero produces no jitter — callbackAfterMs equals base delay exactly"() {
        given: "A task def with zero jitter"
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 3
        taskDef.retryDelaySeconds = 30
        taskDef.retryLogic = TaskDef.RetryLogic.FIXED
        taskDef.backoffJitterMs = 0
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when:
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'no-jitter-test', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "callbackAfterMs is exactly 30_000 (no jitter added)"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED }
            assert rescheduled != null
            def taskModel = executionDAOFacade.getTaskModel(rescheduled.taskId)
            assert taskModel.callbackAfterMs == 30_000
        }
    }

    // -------------------------------------------------------------------------
    // cap + jitter combined
    // -------------------------------------------------------------------------

    def "Cap is applied before jitter — callbackAfterMs is in [cap_ms, cap_ms + maxJitterMs]"() {
        given: "Exponential backoff: base=2s, cap=3s, jitter up to 2000ms"
        // retry 0: raw=2s, below cap; retry 1: raw=4s → capped to 3s; callbackAfterMs in [3000, 5000]
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 5
        taskDef.retryDelaySeconds = 2
        taskDef.retryLogic = TaskDef.RetryLogic.EXPONENTIAL_BACKOFF
        taskDef.maxRetryDelaySeconds = 3
        taskDef.backoffJitterMs = 2000
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when: "Start workflow and fail the task (retry 0 — raw=2s, below cap)"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'cap-jitter-test', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        // wait for retry 0 to be rescheduled before polling again
        conditions.eventually {
            assert workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED } != null
        }

        and: "Fail again (retry 1 — raw=4s → capped to 3s)"
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "callbackAfterSeconds = 3 (the cap) and callbackAfterMs is in [3_000, 5_000]"
        conditions.eventually {
            def rescheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.findAll { it.status == Task.Status.SCHEDULED }.last()
            assert rescheduled != null
            assert rescheduled.callbackAfterSeconds == 3
            def taskModel = executionDAOFacade.getTaskModel(rescheduled.taskId)
            assert taskModel.callbackAfterMs >= 3_000
            assert taskModel.callbackAfterMs <= 5_000
        }
    }

    // =========================================================================
    // totalTimeoutSeconds
    // =========================================================================

    def "totalTimeoutSeconds=0 is disabled — retries continue as normal"() {
        given:
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 5
        taskDef.retryDelaySeconds = 0
        taskDef.retryLogic = TaskDef.RetryLogic.FIXED
        taskDef.totalTimeoutSeconds = 0 // disabled
        taskDef.timeoutSeconds = 3600
        taskDef.responseTimeoutSeconds = 3600
        metadataService.registerTaskDef([taskDef])

        when:
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'total-timeout-disabled', [:], null)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "retry is scheduled (total timeout disabled)"
        conditions.eventually {
            def wf = workflowExecutionService.getExecutionStatus(wfId, true)
            assert wf.tasks.size() >= 2
            assert wf.tasks.last().status == Task.Status.SCHEDULED
        }
    }

    def "totalTimeoutSeconds exceeded on retry — workflow fails with no further retries"() {
        given: "A task with retries allowed but total budget of 5 s"
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 10
        taskDef.retryDelaySeconds = 0
        taskDef.retryLogic = TaskDef.RetryLogic.FIXED
        taskDef.totalTimeoutSeconds = 5
        taskDef.timeoutSeconds = 0      // disabled so constraint (timeoutSeconds ≤ totalTimeout) passes
        taskDef.responseTimeoutSeconds = 1
        taskDef.timeoutPolicy = TaskDef.TimeoutPolicy.TIME_OUT_WF
        metadataService.registerTaskDef([taskDef])

        when: "Start the workflow, burn some time, then fail the task"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'total-timeout-exceeded', [:], null)

        // Retrieve the internal task model and backdate firstScheduledTime to exceed the budget
        conditions.eventually {
            assert workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED } != null
        }
        def scheduled = workflowExecutionService.getExecutionStatus(wfId, true).tasks.first()
        def taskModel = executionDAOFacade.getTaskModel(scheduled.taskId)
        taskModel.firstScheduledTime = System.currentTimeMillis() - 60_000 // 60s ago > 5s budget
        executionDAOFacade.updateTask(taskModel)

        // Now fail the task — retry() should detect total timeout exceeded and terminate workflow
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()))

        then: "Workflow fails (total timeout exceeded, no more retries)"
        conditions.eventually {
            def wf = workflowExecutionService.getExecutionStatus(wfId, false)
            assert wf.status == Workflow.WorkflowStatus.TIMED_OUT || wf.status == Workflow.WorkflowStatus.FAILED
        }
    }

    def "totalTimeoutSeconds exceeded in checkTotalTimeout — IN_PROGRESS task is timed out"() {
        given: "A task with RETRY timeout policy and a 5 s total budget"
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 10
        taskDef.retryDelaySeconds = 0
        taskDef.retryLogic = TaskDef.RetryLogic.FIXED
        taskDef.totalTimeoutSeconds = 5
        taskDef.timeoutSeconds = 0      // disabled so constraint passes
        taskDef.responseTimeoutSeconds = 1
        taskDef.timeoutPolicy = TaskDef.TimeoutPolicy.RETRY
        metadataService.registerTaskDef([taskDef])

        when: "Start the workflow, poll the task (IN_PROGRESS), then backdate firstScheduledTime"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'total-timeout-inprogress', [:], null)
        def polled = pollUntilAvailable()

        // Backdate firstScheduledTime on the IN_PROGRESS task to exceed total budget
        def taskModel = executionDAOFacade.getTaskModel(polled.taskId)
        taskModel.firstScheduledTime = System.currentTimeMillis() - 60_000
        executionDAOFacade.updateTask(taskModel)

        // Trigger a decide cycle (sweep) to fire checkTotalTimeout
        sweep(wfId)

        then: "Task is TIMED_OUT by checkTotalTimeout (RETRY policy sets status, doesn't terminate wf yet)"
        conditions.eventually {
            def wf = workflowExecutionService.getExecutionStatus(wfId, true)
            def timedOutTask = wf.tasks.find { it.taskId == polled.taskId }
            assert timedOutTask != null
            assert timedOutTask.status == Task.Status.TIMED_OUT
        }
    }
}
