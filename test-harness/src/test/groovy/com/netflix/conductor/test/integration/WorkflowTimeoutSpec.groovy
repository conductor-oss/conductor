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

import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.utils.Utils
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.util.concurrent.PollingConditions

/**
 * The workflow timeout is only checked when the workflow is decided, so its decider-queue entry
 * must come due by the workflow deadline even while a task with a later deadline is pending (#1779).
 */
class WorkflowTimeoutSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    def "workflow timeout fires on time while blocked on a #waitKind WAIT"() {
        given: "a workflow with a 3s timeout blocked on a WAIT"
        def name = "workflow_timeout_wait_${waitKind}"
        metadataService.updateWorkflowDef([waitWorkflow(name, 3, waitInput)])

        when: "the workflow is started"
        def workflowId = startWorkflow(name, 1, '', [:], null)

        then: "the WAIT is in progress"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].taskType == TaskType.WAIT.name()
        }

        and: "its decider entry comes due at the 3s deadline, and that decide times it out"
        // Sweep due entries here rather than rely on the background sweeper thread, which does
        // not survive the test context being stopped and restarted between specs. Without the
        // deadline cap the entry is not due until the WAIT (5m) or the workflow offset (30s).
        new PollingConditions(timeout: 15, initialDelay: 2, delay: 0.5).eventually {
            sweepDueWorkflows()
            assert workflowExecutionService.getExecutionStatus(workflowId, false).status ==
                    Workflow.WorkflowStatus.TIMED_OUT
        }

        where:
        waitKind | waitInput
        'timed'  | [duration: '5m']
        'signal' | [:]
    }

    /** Decides every workflow whose decider-queue entry is due now. */
    private void sweepDueWorkflows() {
        for (String workflowId : queueDAO.pop(Utils.DECIDER_QUEUE, 10, 100)) {
            sweep(workflowId)
        }
    }

    private static WorkflowDef waitWorkflow(String name, long timeoutSeconds, Map<String, Object> waitInput) {
        def wait = new WorkflowTask(name: 'wait', taskReferenceName: 'wait', type: TaskType.WAIT.name(),
                inputParameters: waitInput)
        new WorkflowDef(name: name, version: 1, schemaVersion: 2, ownerEmail: 'test@harness.com',
                timeoutSeconds: timeoutSeconds, timeoutPolicy: WorkflowDef.TimeoutPolicy.TIME_OUT_WF,
                tasks: [wait])
    }
}
