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

import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * End-to-end integration test proving that {@code ${workflow.secrets.X}} is resolved for
 * synchronous system tasks (e.g. INLINE) that execute directly in the decide loop, not just at
 * the worker-poll / async-system-task hand-off points.
 * <p>
 * - The persisted task INPUT must stay literal ({@code ${workflow.secrets.SECRET}}).
 * - The task must execute against the resolved secret value, so INLINE's output (which simply
 *   echoes {@code $.value1}) must contain the resolved secret.
 */
class InlineSecretSpec extends AbstractSpecification {

    private static final String WORKFLOW_NAME = 'inline_secret_wf'

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_SECRET", "s3cr3t")
    }

    def cleanupSpec() {
        System.clearProperty("CONDUCTOR_SECRET_SECRET")
    }

    def setup() {
        def wfTask = new WorkflowTask()
        wfTask.name = 'inline_task'
        wfTask.taskReferenceName = 'inline_task_ref'
        wfTask.type = 'INLINE'
        wfTask.inputParameters = [
                evaluatorType: 'graaljs',
                expression   : '(function () { return $.value1; })();',
                value1       : '${workflow.secrets.SECRET}'
        ]

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
        workflowTestUtil.clearWorkflows()
    }

    def "secret is resolved for synchronous INLINE system task while stored input stays literal"() {
        when: "the workflow is started"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'inline-secret-test', [:], null)

        then: "the INLINE task ran synchronously against the resolved secret"
        def status = workflowExecutionService.getExecutionStatus(wfId, true)
        def inlineTask = status.tasks.find { it.referenceTaskName == 'inline_task_ref' }
        inlineTask != null
        inlineTask.outputData.result == 's3cr3t'

        and: "the persisted task input keeps the literal secret reference"
        inlineTask.inputData.value1 == '${workflow.secrets.SECRET}'
    }
}
