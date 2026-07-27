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

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSystemTaskWorkerSpecification
import com.netflix.conductor.test.utils.BlockingSystemTask

import spock.lang.Shared

/**
 * An async system task must never be executed twice under the same task id.
 *
 * SystemTaskWorker acks (removes) the queue message at poll, and AsyncSystemTaskExecutor then invokes
 * a blocking start() without persisting anything first. For the whole invocation the task is therefore
 * SCHEDULED with no queue message — the state WorkflowSweeper's repair treats as a lost message and
 * re-pushes, which hands the task straight back to the worker for a second concurrent invocation.
 */
class AsyncSystemTaskDuplicateExecutionSpec extends AbstractSystemTaskWorkerSpecification {

    @Autowired
    QueueDAO queueDAO

    @Shared
    def BLOCKING_SYSTEM_TASK_WORKFLOW = 'test_blocking_system_task_workflow'

    def setup() {
        BlockingSystemTask.reset()
        workflowTestUtil.registerWorkflows('blocking_system_task_workflow_integration_test.json')
    }

    def cleanup() {
        BlockingSystemTask.release()
    }

    def "a blocked async system task is not executed twice when repair re-queues its message"() {
        when: "the workflow is started and the worker picks up the blocking system task"
        def workflowId = startWorkflow(BLOCKING_SYSTEM_TASK_WORKFLOW, 1, 'duplicate_execution', [:], null)

        then: "start() has been entered exactly once and is still blocked"
        BlockingSystemTask.awaitFirstInvocation(15)
        BlockingSystemTask.invocationCount() == 1

        and: "the task is SCHEDULED and its queue message was removed at poll"
        String taskId = null
        conditions.eventually {
            def task = workflowExecutionService.getExecutionStatus(workflowId, true).tasks
                    .find { it.taskType == BlockingSystemTask.NAME }
            task != null
            task.status == Task.Status.SCHEDULED
            taskId = task.taskId
        }

        when: "the sweeper repairs the workflow while the invocation is still in flight"
        sweep(workflowId)
        // The worker polls this queue every 50ms, so 5s is ~100 poll cycles. Against the pre-fix
        // behaviour the second invocation was observed within ~2s of the repair.
        Thread.sleep(5000)

        then: "the message is still reserved, so start() was not invoked a second time"
        queueDAO.containsMessage(BlockingSystemTask.NAME, taskId)
        BlockingSystemTask.invocationCount() == 1

        cleanup:
        BlockingSystemTask.release()
    }
}
