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
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllableWorker

import spock.util.concurrent.PollingConditions

/**
 * E2E verification of {@code conductor.annotated-workers.mode=poll-worker}: annotated
 * worker tasks execute through the standard worker poll/update path (the same execution model
 * orkes-conductor uses for these task types) instead of the async system-task machinery.
 *
 * <p>Proves two things against the REAL scheduling, queue (redis), poller, and decider:
 *
 * <ul>
 *   <li>Polling works end-to-end: the workflow completes with the worker's output, the task is
 *       claimed by a poll-worker thread, and the task type is NOT registered as a system task.
 *   <li>The #1321 duplicate-execution scenario is structurally impossible: the poll ACKs
 *       (removes) the queue message before the blocking method runs, so there is nothing for the
 *       redelivery machinery to redeliver mid-execution — the operation runs EXACTLY ONCE.
 * </ul>
 */
@TestPropertySource(properties = [
        "conductor.annotated-workers.mode=poll-worker"
])
class AnnotatedWorkerPollModeSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    ControllableWorker controllableWorker

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    Set<WorkflowSystemTask> asyncSystemTasks

    static final String WF = 'controllable_async_system_task_wf'
    static final String QUEUE = ControllableWorker.TASK_TYPE

    def poll = new PollingConditions(timeout: 20, delay: 0.2)

    def setup() {
        controllableWorker.reset()
        workflowTestUtil.registerWorkflows('controllable_async_system_task_workflow.json')
    }

    def "annotated worker task executes through the poll path and completes the workflow"() {
        expect: "in poll-worker mode the annotated type is NOT registered as a system task"
        asyncSystemTasks.find { it.taskType == QUEUE } == null

        and: "a task def was auto-registered with poll-path recovery settings"
        def taskDef = metadataService.getTaskDef(QUEUE)
        taskDef.responseTimeoutSeconds == 3600
        taskDef.retryCount == 3

        when: "a workflow with the annotated task is started (worker not armed: returns immediately)"
        def workflowId = startWorkflow(WF, 1, 'pollmode_' + UUID.randomUUID(), [:], null)

        then: "a poll-worker thread claims and executes it and the workflow completes"
        poll.eventually {
            def wf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert wf.status == Workflow.WorkflowStatus.COMPLETED
            assert wf.tasks.size() == 1
            assert wf.tasks[0].taskType == QUEUE
            assert wf.tasks[0].status == Task.Status.COMPLETED
            assert wf.tasks[0].workerId?.contains('annotated-poll-worker')
            assert wf.output['taskOutput'] == 1
        }
        controllableWorker.invocations.get() == 1
    }

    def "blocking execution cannot be redelivered mid-flight - poll acked the queue message"() {
        given: "the worker is armed to block during its invocation (the long provider call)"
        controllableWorker.enteredRun = new CountDownLatch(1)
        controllableWorker.release = new CountDownLatch(1)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'pollmode1321_' + UUID.randomUUID(), [:], null)

        then: "a poll-worker thread entered the method and is blocking"
        controllableWorker.enteredRun.await(15, TimeUnit.SECONDS)

        and: "the task was claimed at poll time: IN_PROGRESS before the method completed"
        poll.eventually {
            def wf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert wf.tasks[0].status == Task.Status.IN_PROGRESS
            assert wf.tasks[0].workerId?.contains('annotated-poll-worker')
        }

        when: "the redelivery machinery gets every chance to redeliver mid-execution"
        queueDAO.processUnacks(QUEUE)
        Thread.sleep(1500)
        List<String> redelivered = queueDAO.pop(QUEUE, 1, 300)

        then: "nothing to redeliver - the poll ACKED (removed) the message before execution"
        redelivered == null || redelivered.isEmpty()
        controllableWorker.invocations.get() == 1

        when: "the blocked call finally finishes"
        controllableWorker.release.countDown()

        then: "the workflow completes normally with exactly one invocation"
        poll.eventually {
            def wf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert wf.status == Workflow.WorkflowStatus.COMPLETED
        }
        controllableWorker.invocations.get() == 1
    }
}
