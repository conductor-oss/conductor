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

import org.conductoross.conductor.core.exception.SchemaValidationException
import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.SchemaDef
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.MockExternalPayloadStorage

/** Schema enforcement integration tests. Each definition opts in via its own {@code enforceSchema} flag. */
class SchemaEnforcementSpec extends AbstractSpecification {

    @Autowired
    MockExternalPayloadStorage mockExternalPayloadStorage

    /** Requires a string `name`, which is what every payload below either has or lacks. */
    private static SchemaDef requiresName(String schemaName) {
        def schema = new SchemaDef()
        schema.name = schemaName
        schema.version = 1
        schema.type = SchemaDef.Type.JSON
        schema.data = [
                '$schema'   : 'https://json-schema.org/draft/2020-12/schema',
                'type'      : 'object',
                'properties': ['name': ['type': 'string']],
                'required'  : ['name']
        ]
        return schema
    }

    private WorkflowDef register(String label, TaskDef taskDef, Map<String, Object> taskInput,
                                 SchemaDef workflowInput, SchemaDef workflowOutput) {
        metadataService.registerTaskDef([taskDef])

        def workflowTask = new WorkflowTask()
        workflowTask.name = taskDef.name
        workflowTask.taskReferenceName = 'step'
        workflowTask.workflowTaskType = TaskType.SIMPLE
        workflowTask.inputParameters = taskInput

        def workflowDef = new WorkflowDef()
        workflowDef.name = "schema_enforcement_${label}_${UUID.randomUUID().toString().replace('-', '')}"
        workflowDef.version = 1
        workflowDef.ownerEmail = 'test@conductor.io'
        workflowDef.schemaVersion = 2
        workflowDef.inputSchema = workflowInput
        workflowDef.outputSchema = workflowOutput
        workflowDef.tasks = [workflowTask]
        if (workflowOutput != null) {
            workflowDef.outputParameters = ['name': '${step.output.name}']
        }
        metadataService.registerWorkflowDef(workflowDef)
        return workflowDef
    }

    private static TaskDef taskDef(String name, int retryCount, SchemaDef input, SchemaDef output) {
        def taskDef = new TaskDef(name, name, 'test@conductor.io', 5, 120, 120)
        taskDef.retryCount = retryCount
        taskDef.inputSchema = input
        taskDef.outputSchema = output
        taskDef.enforceSchema = true
        return taskDef
    }

    private static String uniqueTaskName(String label) {
        return "schema_task_${label}_${UUID.randomUUID().toString().replace('-', '')}"
    }

    def "a workflow whose input breaks its schema never starts"() {
        given: "a workflow definition with an input schema"
        def workflowDef = register('wfin', taskDef(uniqueTaskName('wfin'), 0, null, null),
                ['name': '${workflow.input.name}'], requiresName('wfin_schema'), null)

        when: "it is started with an input that does not match"
        startWorkflow(workflowDef.name, 1, '', ['nickname': 'ada'], null)

        then: "the start is rejected, naming what failed"
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('name')

        and: "nothing was created"
        workflowExecutionService.getRunningWorkflows(workflowDef.name, 1).isEmpty()
    }

    def "a conforming workflow input starts"() {
        given:
        def workflowDef = register('wfok', taskDef(uniqueTaskName('wfok'), 0, null, null),
                ['name': '${workflow.input.name}'], requiresName('wfok_schema'), null)

        when:
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['name': 'ada'], null)

        then:
        workflowId
        workflowExecutionService.getExecutionStatus(workflowId, true).status == Workflow.WorkflowStatus.RUNNING
    }

    def "a task whose input breaks its schema fails terminally and fails the workflow"() {
        given: "a task with an input schema and three retries left to spend"
        def taskName = uniqueTaskName('tin')
        def workflowDef = register('tin', taskDef(taskName, 3, requiresName('tin_schema'), null),
                ['nickname': '${workflow.input.nickname}'], null, null)

        when: "the workflow starts with an input that leaves the task without a `name`"
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['nickname': 'ada'], null)

        then: "the task failed terminally and took the workflow down with it"
        workflowId
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].status == Task.Status.FAILED_WITH_TERMINAL_ERROR
            tasks[0].reasonForIncompletion.contains('name')
        }

        and: "the retry budget was not spent re-submitting the same invalid payload"
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks.size() == 1
    }

    def "a task whose output breaks its schema fails terminally"() {
        given:
        def taskName = uniqueTaskName('tout')
        def workflowDef = register('tout', taskDef(taskName, 0, null, requiresName('tout_schema')),
                ['name': '${workflow.input.name}'], null, null)
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['name': 'ada'], null)

        when: "a worker completes it with an output that does not match"
        workflowTestUtil.pollAndCompleteTask(taskName, 'schema.worker', ['nickname': 'ada'])

        then: "the task fails terminally, as a bad input does, and the workflow fails"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks[0].status == Task.Status.FAILED_WITH_TERMINAL_ERROR
            tasks[0].reasonForIncompletion.contains('name')
        }
    }

    def "a conforming task output completes the task"() {
        given:
        def taskName = uniqueTaskName('toutok')
        def workflowDef = register('toutok', taskDef(taskName, 0, null, requiresName('toutok_schema')),
                ['name': '${workflow.input.name}'], null, null)
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['name': 'ada'], null)

        when:
        workflowTestUtil.pollAndCompleteTask(taskName, 'schema.worker', ['name': 'ada'])

        then:
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks[0].status == Task.Status.COMPLETED
        }
    }

    def "an externalized output is not checked, rather than rejected for being absent"() {
        given: "a task with an output schema, whose worker returns through external storage"
        def taskName = uniqueTaskName('toutext')
        def workflowDef = register('toutext', taskDef(taskName, 0, null, requiresName('toutext_schema')),
                ['name': '${workflow.input.name}'], null, null)
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['name': 'ada'], null)

        when: "the worker hands over a storage path instead of the payload"
        def outputPath = "${UUID.randomUUID()}.json"
        mockExternalPayloadStorage.upload(outputPath, mockExternalPayloadStorage.readOutputDotJson(), 0)
        workflowTestUtil.pollAndCompleteLargePayloadTask(taskName, 'schema.worker', outputPath)

        // An externalized output leaves `outputData` empty. Checking it would reject every
        // large payload for fields that are in fact present, so the check is skipped.
        then: "the task completes, and the output schema is not applied to an empty outputData"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].externalOutputPayloadStoragePath == outputPath
        }
    }

    def "a workflow whose output breaks its schema fails at completion"() {
        given: "the workflow maps its output from a field the worker never sets"
        def taskName = uniqueTaskName('wfout')
        def workflowDef = register('wfout', taskDef(taskName, 0, null, null),
                ['name': '${workflow.input.name}'], null, requiresName('wfout_schema'))
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['name': 'ada'], null)

        when: "the task completes successfully"
        workflowTestUtil.pollAndCompleteTask(taskName, 'schema.worker', ['other': 'value'])

        then: "the workflow fails at completion instead of completing"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            reasonForIncompletion.contains('name')
        }
    }

    def "a definition that does not opt in is not validated"() {
        given: "the same schema, with enforceSchema off on the task definition"
        def taskName = uniqueTaskName('optout')
        def taskDef = taskDef(taskName, 0, requiresName('optout_schema'), null)
        taskDef.enforceSchema = false
        def workflowDef = register('optout', taskDef, ['nickname': '${workflow.input.nickname}'], null, null)

        when:
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['nickname': 'ada'], null)

        then: "a schema attached without opting in does not reject work"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.SCHEDULED
        }
    }

    def "a schema the registry does not hold leaves the payload unvalidated"() {
        given: "a task whose input schema is a reference to nothing"
        def dangling = new SchemaDef()
        dangling.name = 'never_registered_' + UUID.randomUUID().toString().replace('-', '')
        dangling.version = 4
        def taskName = uniqueTaskName('dangling')
        def workflowDef = register('dangling', taskDef(taskName, 0, dangling, null),
                ['nickname': '${workflow.input.nickname}'], null, null)

        when:
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['nickname': 'ada'], null)

        then: "the task is scheduled as though no schema were attached"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.SCHEDULED
        }
    }
}
