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
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableSystemTask

import spock.util.concurrent.PollingConditions

/**
 * REPRODUCES ISSUE #1322 - Late completion of a duplicate attempt overwrites the terminal task
 * record.
 *
 * <p>When a duplicate attempt (the second worker that popped the same SCHEDULED task - see #1321)
 * finishes AFTER the first attempt already completed the task, its {@code updateTask} overwrites the
 * terminal task record's outputData/endTime/finishReason (last-write-wins). The resulting record has
 * an endTime AFTER the workflow's endTime, and downstream logic (e.g. an agent-loop message history
 * rebuilt from task records) then reads phantom/late values.
 *
 * <p>Fix contract asserted here: {@code ExecutionDAOFacade.updateTask} drops the update (warn log)
 * when the STORED task is already terminal AND the incoming status is also terminal; non-terminal
 * stored tasks and terminal->non-terminal resets (rerun) still persist as today.
 *
 * <p>E2E assertion: after the duplicate-execution flow, the terminal task's stored
 * outputData/endTime must be the FIRST attempt's values (the ones the workflow actually consumed),
 * and the task endTime must not exceed the workflow endTime.
 *
 * <p>Status: FAILS on current code (record holds the late "SECOND" attempt with endTime > workflow
 * endTime) until the fix is applied.
 *
 * <h3>What each step simulates from the real incident</h3>
 * <ul>
 *   <li>Two concurrent {@code execute()} calls on the same SCHEDULED task = the two system-task
 *       workers that both popped the message because the unack window elapsed mid-execution (the
 *       #1321 mechanism). Both load the task while SCHEDULED and both run {@code start()}.</li>
 *   <li>Releasing attempt 1 first, then waiting for workflow COMPLETED = the first attempt finishing
 *       and completing the workflow.</li>
 *   <li>Releasing attempt 2 afterwards = the duplicate attempt finishing LATE, its terminal
 *       {@code updateTask} landing after the workflow already ended.</li>
 * </ul>
 */
class Issue1322LateDuplicateOverwriteSpec extends AbstractSpecification {

    @Autowired
    ControllableSystemTask controllableSystemTask

    static final String WF = 'controllable_async_system_task_wf'

    def setup() {
        controllableSystemTask.reset()
        if (workflowTestUtil.getPersistedTaskDefinition('controllable_task').isEmpty()) {
            TaskDef taskDef = new TaskDef()
            taskDef.name = 'controllable_task'
            taskDef.ownerEmail = 'test@harness.com'
            taskDef.responseTimeoutSeconds = 3600
            taskDef.timeoutSeconds = 3600
            taskDef.retryCount = 0
            metadataService.registerTaskDef([taskDef])
        }
        workflowTestUtil.registerWorkflows('controllable_async_system_task_workflow.json')
    }

    def "late completion of a duplicate attempt must not overwrite the terminal task record"() {
        given: "the controllable system task is armed for two ordered concurrent attempts"
        controllableSystemTask.orderedMode = true
        controllableSystemTask.enteredStart = new CountDownLatch(2)
        controllableSystemTask.releaseAttempt1 = new CountDownLatch(1)
        controllableSystemTask.releaseAttempt2 = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'issue1322_' + UUID.randomUUID(), [:], null)
        def startedWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the async system task is SCHEDULED"
        startedWf.status == Workflow.WorkflowStatus.RUNNING
        startedWf.tasks.size() == 1
        startedWf.tasks[0].status == Task.Status.SCHEDULED

        when: "two system-task workers both execute the same SCHEDULED task (redelivery duplicate)"
        String taskId = startedWf.tasks[0].taskId
        Thread workerA = new Thread({ asyncSystemTaskExecutor.execute(controllableSystemTask, taskId) })
        Thread workerB = new Thread({ asyncSystemTaskExecutor.execute(controllableSystemTask, taskId) })
        workerA.setDaemon(true)
        workerB.setDaemon(true)
        workerA.start()
        workerB.start()

        and: "both attempts have entered start() (both loaded the task while SCHEDULED)"
        assert controllableSystemTask.enteredStart.await(10, TimeUnit.SECONDS)

        and: "the FIRST attempt completes and the workflow reaches COMPLETED"
        controllableSystemTask.releaseAttempt1.countDown()
        def firstAttemptConditions = new PollingConditions(timeout: 10)
        firstAttemptConditions.eventually {
            def wf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert wf.status == Workflow.WorkflowStatus.COMPLETED
            assert wf.getTaskByRefName('controllable_ref').status == Task.Status.COMPLETED
            assert wf.getTaskByRefName('controllable_ref').outputData['marker'] == 'FIRST'
        }
        long workflowEndTime = workflowExecutionService.getExecutionStatus(workflowId, true).endTime

        and: "the DUPLICATE attempt then finishes LATE and its executor persists its terminal update"
        controllableSystemTask.releaseAttempt2.countDown()
        workerA.join(10000)
        workerB.join(10000)
        // Wait until the late attempt has actually written to the store (endTime recorded).
        def lateWriteConditions = new PollingConditions(timeout: 10)
        lateWriteConditions.eventually {
            assert controllableSystemTask.attempt2EndTime > 0
        }

        then: "the stored terminal record must still be the FIRST attempt's, not overwritten by the late one"
        def finalWf = workflowExecutionService.getExecutionStatus(workflowId, true)
        Task storedTask = finalWf.getTaskByRefName('controllable_ref')
        // FAILS pre-fix: last-write-wins leaves 'SECOND'.
        storedTask.outputData['marker'] == 'FIRST'
        // FAILS pre-fix: the late attempt stamped an endTime after the workflow already ended.
        storedTask.endTime <= workflowEndTime

        cleanup:
        controllableSystemTask.releaseAttempt1?.countDown()
        controllableSystemTask.releaseAttempt2?.countDown()
        workerA?.join(5000)
        workerB?.join(5000)
    }
}
