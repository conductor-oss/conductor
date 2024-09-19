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
package com.netflix.conductor.scylla.dao

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef

import spock.lang.Subject

class ScyllaMetadataDAOSpec extends ScyllaSpec {

    @Subject
    ScyllaMetadataDAO metadataDAO

    def setup() {
        metadataDAO = new ScyllaMetadataDAO(session, objectMapper, cassandraProperties, statements)
    }

    def cleanup() {

    }

    def "CRUD on WorkflowDef"() throws Exception {
        given:
        String name = "workflow_def_1"
        int version = 1

        WorkflowDef workflowDef = new WorkflowDef()
        workflowDef.setName(name)
        workflowDef.setVersion(version)
        workflowDef.setOwnerEmail("test@junit.com")

        when: 'create workflow definition'
        metadataDAO.createWorkflowDef(workflowDef)

        then: // fetch the workflow definition
        def defOptional = metadataDAO.getWorkflowDef(name, version)
        defOptional.present
        defOptional.get() == workflowDef

        and: // register a higher version
        int higherVersion = 2
        workflowDef.setVersion(higherVersion)
        workflowDef.setDescription("higher version")

        when: // register the higher version definition
        metadataDAO.createWorkflowDef(workflowDef)
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion)

        then: // fetch the higher version
        defOptional.present
        defOptional.get() == workflowDef

        when: // fetch latest version
        defOptional = metadataDAO.getLatestWorkflowDef(name)

        then:
        defOptional && defOptional.present
        defOptional.get() == workflowDef

        when: // modify the definition
        workflowDef.setOwnerEmail("test@junit.com")
        metadataDAO.updateWorkflowDef(workflowDef)
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion)

        then: // fetch the workflow definition
        defOptional.present
        defOptional.get() == workflowDef

        when: // delete workflow def
        metadataDAO.removeWorkflowDef(name, higherVersion)
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion)

        then:
        defOptional.empty
    }

    def "CRUD on TaskDef"() {
        given:
        String task1Name = "task1"
        String task2Name = "task2"

        when: // fetch all task defs
        def taskDefList = metadataDAO.getAllTaskDefs()

        then:
        taskDefList.empty

        when: // register a task definition
        TaskDef taskDef = new TaskDef()
        taskDef.setName(task1Name)
        metadataDAO.createTaskDef(taskDef)
        taskDefList = metadataDAO.getAllTaskDefs()

        then: // fetch all task defs
        taskDefList && taskDefList.size() == 1

        when: // fetch the task def
        def returnTaskDef = metadataDAO.getTaskDef(task1Name)

        then:
        returnTaskDef == taskDef

        when: // register another task definition
        TaskDef taskDef1 = new TaskDef()
        taskDef1.setName(task2Name)
        metadataDAO.createTaskDef(taskDef1)
        // fetch all task defs
        taskDefList = metadataDAO.getAllTaskDefs()

        then:
        taskDefList && taskDefList.size() == 2

        when: // update task def
        taskDef.setOwnerEmail("juni@test.com")
        metadataDAO.updateTaskDef(taskDef)
        returnTaskDef = metadataDAO.getTaskDef(task1Name)

        then:
        returnTaskDef == taskDef

        when: // delete task def
        metadataDAO.removeTaskDef(task2Name)
        taskDefList = metadataDAO.getAllTaskDefs()

        then:
        taskDefList && taskDefList.size() == 1
        // fetch deleted task def
        metadataDAO.getTaskDef(task2Name) == null
    }

    def "set default response timeout when not set"() {
        given:
        String task1Name = "task1"

        when: // register a task definition
        TaskDef taskDef = new TaskDef()
        taskDef.setName(task1Name)
        taskDef.setResponseTimeoutSeconds(0)
        metadataDAO.createTaskDef(taskDef)
        def returnTaskDef = metadataDAO.getTaskDef(task1Name)

        then:
        returnTaskDef.getResponseTimeoutSeconds() == 3600

        when: // register another task definition
        taskDef.setTimeoutSeconds(200)
        taskDef.setResponseTimeoutSeconds(0)
        metadataDAO.updateTaskDef(taskDef)
        // fetch all task defs
        def taskDefList = metadataDAO.getAllTaskDefs()

        then:
        taskDefList && taskDefList.size() == 1
        taskDefList.get(0).getResponseTimeoutSeconds() == 199

    }

    def "parse index string"() {
        expect:
        def pair = metadataDAO.getWorkflowNameAndVersion(nameVersionStr)
        pair.left == workflowName
        pair.right == version

        where:
        nameVersionStr << ['name/1', 'namespace/name/3', '/namespace/name_with_lodash/2', 'name//4', 'name-with$%/895']
        workflowName << ['name', 'namespace/name', '/namespace/name_with_lodash', 'name/', 'name-with$%']
        version << [1, 3, 2, 4, 895]
    }

    def "parse index string - incorrect values"() {
        when:
        metadataDAO.getWorkflowNameAndVersion("name_with_no_version")

        then:
        def ex = thrown(IllegalStateException.class)
        println(ex.message)

        when:
        metadataDAO.getWorkflowNameAndVersion("name_with_no_version/")

        then:
        ex = thrown(IllegalStateException.class)
        println(ex.message)

        when:
        metadataDAO.getWorkflowNameAndVersion("name/non_number_version")

        then:
        ex = thrown(IllegalStateException.class)
        println(ex.message)
    }
}
