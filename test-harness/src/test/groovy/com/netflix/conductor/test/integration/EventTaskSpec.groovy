/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.Event
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class EventTaskSpec extends AbstractSpecification {

    def EVENT_BASED_WORKFLOW = 'test_event_workflow'

    @Autowired
    Event eventTask

    @Autowired
    QueueDAO queueDAO

    def setup() {
        workflowTestUtil.registerWorkflows('event_workflow_integration_test.json')
    }

    def "Verify that a event based simple workflow is executed"() {
        when: "Start a event based workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow(EVENT_BASED_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the event task is executed by issuing a system task call"
        List<String> polledTaskIds = queueDAO.pop(eventTask.taskType, 1, 200)
        String eventTaskId = polledTaskIds.get(0)
        asyncSystemTaskExecutor.execute(eventTask, eventTaskId)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == TaskType.EVENT.name()
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData['event_produced']
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The integration_task_1 is polled and completed"
        def polledAndCompletedTry1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task was polled and completed and the workflow is in a complete state"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.COMPLETED
        }
    }
}
