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

import com.netflix.conductor.common.metadata.SchemaDef
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * A definition that attaches schemas but leaves {@code enforceSchema} at its default.
 *
 * <p>This is the guarantee the feature is built around: attaching a schema changes nothing about
 * how a definition executes until that definition also opts in. With no server-wide switch, this
 * flag is the only thing standing between a schema attached for documentation and rejected work,
 * so the default has to be load-bearing rather than incidental. {@link SchemaEnforcementSpec} is
 * the same shape with the flag set.
 */
class SchemaEnforcementDisabledSpec extends AbstractSpecification {

    def "schemas attached to definitions are inert until the definition opts in"() {
        given: "definitions carrying schemas that the payloads below all violate"
        def schema = new SchemaDef()
        schema.name = 'requires_name'
        schema.version = 1
        schema.type = SchemaDef.Type.JSON
        schema.data = [
                '$schema' : 'https://json-schema.org/draft/2020-12/schema',
                'type'    : 'object',
                'required': ['name']
        ]

        def suffix = UUID.randomUUID().toString().replace('-', '')
        def taskDef = new TaskDef("schema_off_task_$suffix", "schema_off_task_$suffix",
                'test@conductor.io', 5, 120, 120)
        taskDef.retryCount = 0
        taskDef.inputSchema = schema
        taskDef.outputSchema = schema
        // Left at its default, which is the point: the schemas above are attached and complete,
        // and nothing but this flag keeps them from rejecting every payload below.
        taskDef.enforceSchema = false
        metadataService.registerTaskDef([taskDef])

        def workflowTask = new WorkflowTask()
        workflowTask.name = taskDef.name
        workflowTask.taskReferenceName = 'step'
        workflowTask.workflowTaskType = TaskType.SIMPLE
        workflowTask.inputParameters = ['nickname': '${workflow.input.nickname}']

        def workflowDef = new WorkflowDef()
        workflowDef.name = "schema_off_wf_$suffix"
        workflowDef.version = 1
        workflowDef.ownerEmail = 'test@conductor.io'
        workflowDef.schemaVersion = 2
        workflowDef.inputSchema = schema
        workflowDef.outputSchema = schema
        workflowDef.enforceSchema = false
        workflowDef.tasks = [workflowTask]
        metadataService.registerWorkflowDef(workflowDef)

        when: "the workflow is started with an input that matches none of them"
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['nickname': 'ada'], null)

        then: "it starts, and the task is scheduled as it always was"
        workflowId
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "a worker returns an output that matches none of them either"
        workflowTestUtil.pollAndCompleteTask(taskDef.name, 'schema.worker', ['other': 'value'])

        then: "the workflow completes"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks[0].status == Task.Status.COMPLETED
        }
    }
}
