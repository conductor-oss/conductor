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
 * synchronous system tasks (SET_VARIABLE and JSON_JQ_TRANSFORM) that execute directly in the
 * decide loop, not just at the worker-poll / async-system-task hand-off points.
 * <p>
 * - The persisted task INPUT must stay literal ({@code ${workflow.secrets.SECRET}}).
 * - The task must execute against the resolved secret value.
 */
class SyncSystemTaskSecretSpec extends AbstractSpecification {

    private static final String SET_VARIABLE_WORKFLOW_NAME = 'set_variable_secret_wf'
    private static final String JSON_JQ_TRANSFORM_WORKFLOW_NAME = 'json_jq_transform_secret_wf'

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_SECRET", "s3cr3t")
    }

    def cleanupSpec() {
        System.clearProperty("CONDUCTOR_SECRET_SECRET")
    }

    def setup() {
        def setVariableTask = new WorkflowTask()
        setVariableTask.name = 'set_variable_task'
        setVariableTask.taskReferenceName = 'set_variable_task_ref'
        setVariableTask.type = 'SET_VARIABLE'
        setVariableTask.inputParameters = [
                secretVar: '${workflow.secrets.SECRET}'
        ]

        def setVariableWfDef = new WorkflowDef()
        setVariableWfDef.name = SET_VARIABLE_WORKFLOW_NAME
        setVariableWfDef.version = 1
        setVariableWfDef.tasks = [setVariableTask]
        setVariableWfDef.ownerEmail = 'test@example.com'
        setVariableWfDef.timeoutSeconds = 3600
        setVariableWfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(setVariableWfDef)

        def jsonJqTransformTask = new WorkflowTask()
        jsonJqTransformTask.name = 'json_jq_transform_task'
        jsonJqTransformTask.taskReferenceName = 'json_jq_transform_task_ref'
        jsonJqTransformTask.type = 'JSON_JQ_TRANSFORM'
        jsonJqTransformTask.inputParameters = [
                secret         : '${workflow.secrets.SECRET}',
                queryExpression: '{ out: .secret }'
        ]

        def jsonJqTransformWfDef = new WorkflowDef()
        jsonJqTransformWfDef.name = JSON_JQ_TRANSFORM_WORKFLOW_NAME
        jsonJqTransformWfDef.version = 1
        jsonJqTransformWfDef.tasks = [jsonJqTransformTask]
        jsonJqTransformWfDef.ownerEmail = 'test@example.com'
        jsonJqTransformWfDef.timeoutSeconds = 3600
        jsonJqTransformWfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(jsonJqTransformWfDef)
    }

    def cleanup() {
        try { metadataService.unregisterWorkflowDef(SET_VARIABLE_WORKFLOW_NAME, 1) } catch (ignored) {}
        try { metadataService.unregisterWorkflowDef(JSON_JQ_TRANSFORM_WORKFLOW_NAME, 1) } catch (ignored) {}
        workflowTestUtil.clearWorkflows()
    }

    def "secret is resolved for synchronous SET_VARIABLE system task while stored input stays literal"() {
        when: "the workflow is started"
        def wfId = startWorkflow(SET_VARIABLE_WORKFLOW_NAME, 1, 'set-variable-secret-test', [:], null)

        then: "the SET_VARIABLE task ran synchronously and wrote the resolved secret into workflow variables"
        def status = workflowExecutionService.getExecutionStatus(wfId, true)
        status.variables.secretVar == 's3cr3t'

        and: "the persisted task input keeps the literal secret reference"
        def setVariableTask = status.tasks.find { it.referenceTaskName == 'set_variable_task_ref' }
        setVariableTask != null
        setVariableTask.inputData.secretVar == '${workflow.secrets.SECRET}'
    }

    def "secret is resolved for synchronous JSON_JQ_TRANSFORM system task while stored input stays literal"() {
        when: "the workflow is started"
        def wfId = startWorkflow(JSON_JQ_TRANSFORM_WORKFLOW_NAME, 1, 'json-jq-transform-secret-test', [:], null)

        then: "the JSON_JQ_TRANSFORM task ran synchronously against the resolved secret"
        def status = workflowExecutionService.getExecutionStatus(wfId, true)
        def jsonJqTransformTask = status.tasks.find { it.referenceTaskName == 'json_jq_transform_task_ref' }
        jsonJqTransformTask != null
        jsonJqTransformTask.outputData.result.out == 's3cr3t'

        and: "the persisted task input keeps the literal secret reference"
        jsonJqTransformTask.inputData.secret == '${workflow.secrets.SECRET}'
    }
}
