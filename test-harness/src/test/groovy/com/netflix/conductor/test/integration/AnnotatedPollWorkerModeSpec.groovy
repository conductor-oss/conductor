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
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.ControllablePollWorker

/**
 * Acceptance tests for {@code conductor.annotated-workers.mode=poll-worker}: annotated
 * {@code @WorkerTask} methods executed through the standard worker-task contract instead of the
 * async system-task path.
 *
 * <p>Covers the two scenarios behind issues #1321/#1322:
 *
 * <ul>
 *   <li>A single blocking invocation that outlasts any queue redelivery window must execute
 *       exactly once. In poll-worker mode this holds structurally: the poll claims the task
 *       (IN_PROGRESS + workerId persisted) and ACKs the queue message BEFORE the method runs, so
 *       there is nothing left in the queue to redeliver.
 *   <li>A multi-turn worker (IN_PROGRESS + callbackAfterSeconds, the long-running LLM/A2A
 *       pattern) must execute each turn exactly once — the scenario the system-task path leaves
 *       unprotected for turns 2+.
 * </ul>
 */
@TestPropertySource(properties = [
        "conductor.annotated-workers.mode=poll-worker",
        "conductor.annotated-workers.response-timeout-seconds=45"
])
class AnnotatedPollWorkerModeSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    ControllablePollWorker worker

    static final String WF = 'controllable_poll_worker_wf'
    static final String QUEUE = ControllablePollWorker.TASK_TYPE

    def setup() {
        worker.reset()
        workflowTestUtil.registerWorkflows('controllable_poll_worker_workflow.json')
    }

    def "blocking annotated task in poll-worker mode executes exactly once"() {
        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'pollworker_' + UUID.randomUUID(), [:], null)

        then: "a poll-worker claims and invokes the method"
        worker.enteredRun.await(10, TimeUnit.SECONDS)

        when: "the invocation blocks well past any queue redelivery window"
        Thread.sleep(3000)
        def runningWf = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "the task was claimed through the worker contract before the method ran"
        runningWf.status == Workflow.WorkflowStatus.RUNNING
        runningWf.tasks.size() == 1
        runningWf.tasks[0].status == Task.Status.IN_PROGRESS
        runningWf.tasks[0].workerId?.startsWith('annotated-poll-worker')

        and: "the queue holds nothing to redeliver - the message was ACKed at claim time"
        queueDAO.getSize(QUEUE) == 0

        and: "the method ran exactly once"
        assert worker.invocations.get() == 1:
                "duplicate invocation detected:\n${worker.invocationLog.join('\n')}"

        when: "the simulated provider call finishes"
        worker.release.countDown()

        then: "the workflow completes with the single attempt's output"
        conditions.eventually {
            def completedWf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert completedWf.status == Workflow.WorkflowStatus.COMPLETED
            assert completedWf.tasks[0].outputData['attempt'] == 1
        }
        worker.invocations.get() == 1

        cleanup:
        worker.release?.countDown()
    }

    def "multi-turn annotated task executes each turn exactly once"() {
        given: "turn 1 returns IN_PROGRESS with a 1s callback; turn 2 blocks mid-call"
        worker.inProgressTurns = 1
        worker.callbackAfterSeconds = 1
        worker.blockFromAttempt = 2
        worker.enteredRun = new CountDownLatch(2)

        when: "the workflow is started"
        def workflowId = startWorkflow(WF, 1, 'pollworker_turn2_' + UUID.randomUUID(), [:], null)

        then: "turn 1 completes and turn 2 is re-claimed after the callback fires"
        worker.enteredRun.await(15, TimeUnit.SECONDS)

        when: "turn 2 blocks well past any queue redelivery window"
        Thread.sleep(3000)

        then: "each turn ran exactly once - no duplicate of the in-flight turn"
        assert worker.invocations.get() == 2:
                "duplicate invocation detected:\n${worker.invocationLog.join('\n')}"

        and: "the queue holds nothing to redeliver for the in-flight turn"
        queueDAO.getSize(QUEUE) == 0

        when: "the simulated provider call finishes"
        worker.release.countDown()

        then: "the workflow completes with turn 2's output"
        conditions.eventually {
            def completedWf = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert completedWf.status == Workflow.WorkflowStatus.COMPLETED
            assert completedWf.tasks[0].outputData['attempt'] == 2
        }
        worker.invocations.get() == 2

        cleanup:
        worker.release?.countDown()
    }
}
