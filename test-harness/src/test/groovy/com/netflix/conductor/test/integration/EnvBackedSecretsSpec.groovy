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

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * End-to-end integration test proving env-backed secrets/environment support works through the
 * full Spring server:
 * - {@code ${workflow.env.<NAME>}} resolves eagerly at schedule time from CONDUCTOR_ENV_<NAME>.
 * - {@code ${workflow.secrets.<NAME>}} stays literal in the persisted task input, and is only
 *   resolved from CONDUCTOR_SECRET_<NAME> at hand-off (on worker poll).
 */
class EnvBackedSecretsSpec extends AbstractSpecification {

    @Autowired
    ExecutionDAOFacade executionDAOFacade

    private static final String TASK_NAME = 'echo_task'
    private static final String WORKFLOW_NAME = 'env_backed_secrets_wf'

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_DB_PASSWORD", "s3cr3t")
        System.setProperty("CONDUCTOR_ENV_REGION", "us-east-1")
    }

    def cleanupSpec() {
        System.clearProperty("CONDUCTOR_SECRET_DB_PASSWORD")
        System.clearProperty("CONDUCTOR_ENV_REGION")
    }

    def setup() {
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 0
        taskDef.timeoutSeconds = 120
        taskDef.responseTimeoutSeconds = 120
        taskDef.ownerEmail = 'test@example.com'
        metadataService.registerTaskDef([taskDef])

        def wfTask = new WorkflowTask()
        wfTask.name = TASK_NAME
        wfTask.taskReferenceName = 'echo_task_ref'
        wfTask.type = TaskType.SIMPLE
        wfTask.inputParameters = [pwd: '${workflow.secrets.DB_PASSWORD}', region: '${workflow.env.REGION}']

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
        try { metadataService.unregisterTaskDef(TASK_NAME) } catch (ignored) {}
        workflowTestUtil.clearWorkflows()
    }

    def "secret stays literal in stored input but resolves on poll; env resolves eagerly"() {
        when: "the workflow is started"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'env-backed-secrets-test', [:], null)

        then: "the persisted scheduled task keeps the literal secret but has the env resolved"
        def scheduled = null
        conditions.eventually {
            scheduled = workflowExecutionService.getExecutionStatus(wfId, true)
                    .tasks.find { it.status == Task.Status.SCHEDULED }
            assert scheduled != null
        }
        def taskModel = executionDAOFacade.getTaskModel(scheduled.taskId)
        taskModel.inputData.pwd == '${workflow.secrets.DB_PASSWORD}'
        taskModel.inputData.region == 'us-east-1'

        when: "a worker polls the task"
        def polled = workflowExecutionService.poll(TASK_NAME, 'test-worker')

        then: "the polled task has the secret resolved"
        polled != null
        polled.inputData.pwd == 's3cr3t'
        polled.inputData.region == 'us-east-1'
    }
}
