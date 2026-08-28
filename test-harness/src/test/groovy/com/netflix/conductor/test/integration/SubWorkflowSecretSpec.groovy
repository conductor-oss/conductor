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

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW

/**
 * End-to-end integration test proving that {@code ${workflow.secrets.X}} is resolved for a task
 * running INSIDE a SUB_WORKFLOW (i.e. resolved against the child workflow's own secret context),
 * not just for tasks in the root/parent workflow.
 * <p>
 * - The persisted task INPUT on the child task must stay literal ({@code ${workflow.secrets.SECRET}}).
 * - The child task must execute against the resolved secret value, so INLINE's output (which simply
 *   echoes {@code $.value1}) must contain the resolved secret.
 */
class SubWorkflowSecretSpec extends AbstractSpecification {

    @Autowired
    SubWorkflow subWorkflowTask

    private static final String CHILD_WORKFLOW_NAME = 'child_secret_wf'
    private static final String PARENT_WORKFLOW_NAME = 'parent_secret_wf'

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_SECRET", "s3cr3t")
    }

    def cleanupSpec() {
        System.clearProperty("CONDUCTOR_SECRET_SECRET")
    }

    def setup() {
        def childTask = new WorkflowTask()
        childTask.name = 'child_inline_task'
        childTask.taskReferenceName = 'child_inline_ref'
        childTask.type = 'INLINE'
        childTask.inputParameters = [
                evaluatorType: 'graaljs',
                expression   : '(function () { return $.value1; })();',
                value1       : '${workflow.secrets.SECRET}'
        ]

        def childWfDef = new WorkflowDef()
        childWfDef.name = CHILD_WORKFLOW_NAME
        childWfDef.version = 1
        childWfDef.tasks = [childTask]
        childWfDef.ownerEmail = 'test@example.com'
        childWfDef.timeoutSeconds = 3600
        childWfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(childWfDef)

        def subWorkflowParam = new SubWorkflowParams()
        subWorkflowParam.name = CHILD_WORKFLOW_NAME
        subWorkflowParam.version = 1

        def subWfTask = new WorkflowTask()
        subWfTask.name = 'subwf_task'
        subWfTask.taskReferenceName = 'subwf_ref'
        subWfTask.type = 'SUB_WORKFLOW'
        subWfTask.subWorkflowParam = subWorkflowParam
        subWfTask.inputParameters = [:]

        def parentWfDef = new WorkflowDef()
        parentWfDef.name = PARENT_WORKFLOW_NAME
        parentWfDef.version = 1
        parentWfDef.tasks = [subWfTask]
        parentWfDef.ownerEmail = 'test@example.com'
        parentWfDef.timeoutSeconds = 3600
        parentWfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(parentWfDef)
    }

    def cleanup() {
        try { metadataService.unregisterWorkflowDef(PARENT_WORKFLOW_NAME, 1) } catch (ignored) {}
        try { metadataService.unregisterWorkflowDef(CHILD_WORKFLOW_NAME, 1) } catch (ignored) {}
        workflowTestUtil.clearWorkflows()
    }

    def "secret is resolved for a task running inside a sub-workflow while stored child input stays literal"() {
        when: "the parent workflow is started"
        def parentId = startWorkflow(PARENT_WORKFLOW_NAME, 1, 'subwf-secret-test', [:], null)

        then: "the SUB_WORKFLOW task is scheduled on the parent"
        conditions.eventually {
            def subWfTask = workflowExecutionService.getExecutionStatus(parentId, true)
                    .tasks.find { it.taskType == TASK_TYPE_SUB_WORKFLOW }
            assert subWfTask != null
        }

        when: "the SUB_WORKFLOW task is driven to launch the child workflow"
        def subWfTask = workflowExecutionService.getExecutionStatus(parentId, true)
                .tasks.find { it.taskType == TASK_TYPE_SUB_WORKFLOW }
        asyncSystemTaskExecutor.execute(subWorkflowTask, subWfTask.taskId)

        then: "the child workflow id is captured off the parent's SUB_WORKFLOW task"
        def childId
        conditions.eventually {
            childId = workflowExecutionService.getExecutionStatus(parentId, true)
                    .tasks.find { it.taskType == TASK_TYPE_SUB_WORKFLOW }.subWorkflowId
            assert childId != null
        }

        when: "the child workflow is swept so its synchronous INLINE task runs"
        sweep(childId)

        then: "the child workflow completes"
        conditions.eventually {
            assert workflowExecutionService.getExecutionStatus(childId, true).status == Workflow.WorkflowStatus.COMPLETED
        }

        and: "the child's INLINE task ran against the resolved secret"
        def childInlineTask = workflowExecutionService.getExecutionStatus(childId, true)
                .tasks.find { it.referenceTaskName == 'child_inline_ref' }
        childInlineTask != null
        childInlineTask.outputData.result == 's3cr3t'

        and: "the persisted child task input keeps the literal secret reference"
        childInlineTask.inputData.value1 == '${workflow.secrets.SECRET}'

        when: "the parent workflow is swept"
        sweep(parentId)

        then: "the parent workflow completes"
        conditions.eventually {
            assert workflowExecutionService.getExecutionStatus(parentId, true).status == Workflow.WorkflowStatus.COMPLETED
        }
    }
}
