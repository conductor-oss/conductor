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
import org.conductoross.conductor.service.SchemaService
import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.SchemaDef
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * Which registered version a workflow definition's schema reference is validated against, driven
 * through a real workflow start rather than the service alone.
 *
 * <p>Three versions live under one name, each requiring a differently named field, so the version
 * applied is visible from whether a start is accepted: an input carrying only {@code three}
 * satisfies version 3 and neither of the others.
 *
 * <p>{@link SchemaEnforcementSpec} covers enforcement itself, with inline schemas. This covers
 * resolution, with registry references.
 */
class SchemaVersionResolutionSpec extends AbstractSpecification {

    @Autowired
    SchemaService schemaService

    /** Unique per run, so a re-run does not read versions left by the previous one. */
    private String schemaName

    def setup() {
        schemaName = "order_${UUID.randomUUID().toString().replace('-', '')}"
        register(1, 'one')
        register(2, 'two')
        register(3, 'three')
    }

    /** Registers version {@code version} of the run's schema, requiring exactly {@code field}. */
    private void register(int version, String field) {
        def schema = new SchemaDef()
        schema.name = schemaName
        schema.version = version
        schema.type = SchemaDef.Type.JSON
        schema.data = [
                '$schema'   : 'https://json-schema.org/draft/2020-12/schema',
                'type'      : 'object',
                'properties': [(field): ['type': 'string']],
                'required'  : [field]
        ]
        schemaService.saveSchema(schema, false)
    }

    /**
     * A workflow whose input schema is a bare registry reference: a name and a version, no inline
     * document. {@code version} below 1 asks for the latest.
     */
    private WorkflowDef registerWorkflowReferencing(int version) {
        def taskName = "schema_version_task_${UUID.randomUUID().toString().replace('-', '')}"
        def taskDef = new TaskDef(taskName, taskName, 'test@conductor.io', 5, 120, 120)
        taskDef.retryCount = 0
        metadataService.registerTaskDef([taskDef])

        def workflowTask = new WorkflowTask()
        workflowTask.name = taskName
        workflowTask.taskReferenceName = 'step'
        workflowTask.workflowTaskType = TaskType.SIMPLE

        def reference = new SchemaDef()
        reference.name = schemaName
        reference.version = version

        def workflowDef = new WorkflowDef()
        workflowDef.name = "schema_version_wf_${UUID.randomUUID().toString().replace('-', '')}"
        workflowDef.version = 1
        workflowDef.ownerEmail = 'test@conductor.io'
        workflowDef.schemaVersion = 2
        workflowDef.enforceSchema = true
        workflowDef.inputSchema = reference
        workflowDef.tasks = [workflowTask]
        metadataService.registerWorkflowDef(workflowDef)
        return workflowDef
    }

    def "a workflow referencing version 3 is validated against version 3"() {
        given: "a definition whose input schema names version 3"
        def workflowDef = registerWorkflowReferencing(3)

        when: "it is started with the input version 3 requires"
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        then: "the start is accepted"
        workflowId
        workflowExecutionService.getExecutionStatus(workflowId, true).status == Workflow.WorkflowStatus.RUNNING
    }

    def "a workflow referencing version 3 rejects input written for another version"() {
        given:
        def workflowDef = registerWorkflowReferencing(3)

        when: "it is started with the input version 2 requires"
        startWorkflow(workflowDef.name, 1, '', ['two': 'x'], null)

        then: "version 3 rejected it, and says which field it wanted"
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('three')
    }

    def "a workflow referencing version 2 is validated against version 2"() {
        given:
        def workflowDef = registerWorkflowReferencing(2)

        when:
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['two': 'x'], null)

        then:
        workflowId
        workflowExecutionService.getExecutionStatus(workflowId, true).status == Workflow.WorkflowStatus.RUNNING
    }

    def "a workflow referencing version 2 rejects input written for the latest version"() {
        given: "a definition pinned to version 2, with version 3 also registered"
        def workflowDef = registerWorkflowReferencing(2)

        when: "it is started with the input the latest version requires"
        startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        then: "the pin held: version 2 was applied, not the newest available"
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('two')
    }

    def "a workflow asking for the latest is validated against version 3"() {
        given: "a reference carrying a version below 1, which asks for the latest"
        def workflowDef = registerWorkflowReferencing(0)

        when:
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        then: "the newest registered version was applied"
        workflowId
        workflowExecutionService.getExecutionStatus(workflowId, true).status == Workflow.WorkflowStatus.RUNNING
    }

    def "a workflow asking for the latest rejects input written for an older version"() {
        given:
        def workflowDef = registerWorkflowReferencing(0)

        when:
        startWorkflow(workflowDef.name, 1, '', ['one': 'x'], null)

        then:
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('three')
    }

    /**
     * A reference written without a version follows the registry: {@code SchemaDef}'s version
     * field defaults to 0, and anything below 1 means "latest", so the reference resolves version
     * 3 -- the newest of the three -- without naming it.
     */
    def "a workflow whose reference omits the version is validated against the latest"() {
        given: "a definition whose input schema names the schema but no version"
        def taskName = "schema_version_task_${UUID.randomUUID().toString().replace('-', '')}"
        def taskDef = new TaskDef(taskName, taskName, 'test@conductor.io', 5, 120, 120)
        taskDef.retryCount = 0
        metadataService.registerTaskDef([taskDef])

        def workflowTask = new WorkflowTask()
        workflowTask.name = taskName
        workflowTask.taskReferenceName = 'step'
        workflowTask.workflowTaskType = TaskType.SIMPLE

        def reference = new SchemaDef()
        reference.name = schemaName
        // version deliberately untouched, which means "latest"

        def workflowDef = new WorkflowDef()
        workflowDef.name = "schema_version_wf_${UUID.randomUUID().toString().replace('-', '')}"
        workflowDef.version = 1
        workflowDef.ownerEmail = 'test@conductor.io'
        workflowDef.schemaVersion = 2
        workflowDef.enforceSchema = true
        workflowDef.inputSchema = reference
        workflowDef.tasks = [workflowTask]
        metadataService.registerWorkflowDef(workflowDef)

        expect: "the untouched field reads 0, which is how a reference asks for the latest"
        reference.version == 0

        when: "it is started with the input the newest version requires"
        def workflowId = startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        then: "version 3 was applied"
        workflowId
        workflowExecutionService.getExecutionStatus(workflowId, true).status == Workflow.WorkflowStatus.RUNNING
    }

    def "a workflow whose reference omits the version rejects input written for an older version"() {
        given: "a definition whose input schema names the schema but no version"
        def workflowDef = registerWorkflowReferencing(0)

        when: "it is started with the input the oldest version requires"
        startWorkflow(workflowDef.name, 1, '', ['one': 'x'], null)

        then: "the latest was applied, so an older version's input does not satisfy it"
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('three')
    }

    /**
     * The point of resolving the latest rather than pinning: a definition registered before
     * version 4 existed starts enforcing it, with no edit to the definition.
     */
    def "a workflow whose reference omits the version picks up a newly registered version"() {
        given: "a definition that omits the version, conforming to version 3 today"
        def workflowDef = registerWorkflowReferencing(0)
        startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        when: "a fourth version is registered and the same definition is started again"
        register(4, 'four')
        startWorkflow(workflowDef.name, 1, '', ['three': 'x'], null)

        then: "version 4 is now what the reference resolves to"
        def thrown = thrown(SchemaValidationException)
        thrown.message.contains('four')
    }
}
