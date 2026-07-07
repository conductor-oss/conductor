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

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * End-to-end integration test proving task-declared secrets injection works through the full
 * Spring server: a {@code TaskDef} declares the secret/environment names it needs via
 * {@code TaskDef.injectedValueKeys}; at poll time the server resolves each name (secrets store
 * first, then environment fallback) and injects the resolved name-&gt;value pairs onto the polled
 * {@code Task.injectedValues} map. Names that resolve in neither provider are omitted.
 */
class TaskDeclaredSecretsSpec extends AbstractSpecification {

    private static final String TASK_NAME = 'declared_secret_task'
    private static final String WORKFLOW_NAME = 'declared_secret_wf'

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_API_KEY", "key-123")
        System.setProperty("CONDUCTOR_ENV_REGION", "us-east-1")
    }

    def cleanupSpec() {
        System.clearProperty("CONDUCTOR_SECRET_API_KEY")
        System.clearProperty("CONDUCTOR_ENV_REGION")
    }

    def setup() {
        def taskDef = new TaskDef()
        taskDef.name = TASK_NAME
        taskDef.retryCount = 0
        taskDef.timeoutSeconds = 120
        taskDef.responseTimeoutSeconds = 120
        taskDef.ownerEmail = 'test@example.com'
        taskDef.setInjectedValueKeys(["API_KEY", "REGION", "MISSING_ONE"])
        metadataService.registerTaskDef([taskDef])

        def wfTask = new WorkflowTask()
        wfTask.name = TASK_NAME
        wfTask.taskReferenceName = 'declared_secret_task_ref'
        wfTask.type = TaskType.SIMPLE

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

    def "declared secret names are resolved from secrets store and env, missing names omitted"() {
        when: "the workflow is started"
        def wfId = startWorkflow(WORKFLOW_NAME, 1, 'declared-secrets-test', [:], null)

        then: "a worker polls the task and receives the resolved secrets map"
        def polled = null
        conditions.eventually {
            polled = workflowExecutionService.poll(TASK_NAME, 'test-worker')
            assert polled != null
        }

        wfId != null
        polled.injectedValues['API_KEY'] == 'key-123'
        polled.injectedValues['REGION'] == 'us-east-1'
        !polled.injectedValues.containsKey('MISSING_ONE')
        polled.injectedValues.size() == 2

        and: "the resolved values are NOT persisted — a re-fetch of the task has no secrets"
        workflowExecutionService.getTask(polled.taskId).injectedValues.isEmpty()
    }
}
