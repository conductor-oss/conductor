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

import java.util.concurrent.TimeUnit

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import static org.awaitility.Awaitility.await

/** Integration coverage for poll-time claiming and response-timeout recovery of system tasks. */
class SystemTaskPollClaimSpec extends AbstractSpecification {

    static final String WF = 'system_task_poll_claim_wf'
    static final String TASK_DEF = 'poll_claim_http_task'
    static final String QUEUE = 'HTTP'
    static final int RESPONSE_TIMEOUT_SECONDS = 2

    @Autowired
    QueueDAO queueDAO

    def setup() {
        TaskDef taskDef = new TaskDef()
        taskDef.name = TASK_DEF
        taskDef.ownerEmail = 'test@harness.com'
        taskDef.responseTimeoutSeconds = RESPONSE_TIMEOUT_SECONDS
        taskDef.timeoutSeconds = 60
        taskDef.timeoutPolicy = TimeoutPolicy.RETRY
        taskDef.retryCount = 1
        taskDef.retryDelaySeconds = 0
        metadataService.registerTaskDef([taskDef])
        workflowTestUtil.registerWorkflows('system_task_poll_claim_workflow.json')
    }

    def "a claimed system task is not redelivered early and is retried after its response timeout"() {
        when: "an HTTP system task is scheduled and claimed without executing it"
        def workflowId = startWorkflow(WF, 1, 'poll_claim_' + UUID.randomUUID(), [:], null)
        List<Task> claimed = workflowExecutionService.pollQueue(QUEUE, 'failed-system-task-node', 1, 100)

        then: "the poll persists the claim and acknowledges the original queue message"
        claimed.size() == 1
        claimed[0].status == Task.Status.IN_PROGRESS
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0].status == Task.Status.IN_PROGRESS
        queueDAO.pop(QUEUE, 1, 100).isEmpty()

        when: "the configured response timeout has not elapsed"
        Thread.sleep(500)

        then: "the original message is still not redelivered"
        queueDAO.pop(QUEUE, 1, 100).isEmpty()
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks.size() == 1

        when: "the claiming node never responds and the response timeout elapses"
        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until {
            workflowExecutor.decide(workflowId)
            Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)
            workflow.tasks.size() == 2 && queueDAO.getSize(QUEUE) == 1
        }
        Workflow recovered = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "Conductor times out that attempt and enqueues one new retry attempt"
        recovered.status == Workflow.WorkflowStatus.RUNNING
        recovered.tasks[0].status == Task.Status.TIMED_OUT
        recovered.tasks[1].status == Task.Status.SCHEDULED
        recovered.tasks[1].retriedTaskId == recovered.tasks[0].taskId
        recovered.tasks[1].taskId != claimed[0].taskId
        workflowExecutionService.pollQueue(QUEUE, 'recovery-system-task-node', 1, 100)*.taskId ==
                [recovered.tasks[1].taskId]
    }
}
