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
package com.netflix.conductor.model

import com.netflix.conductor.common.config.ObjectMapperProvider
import com.netflix.conductor.common.metadata.workflow.WorkflowDef

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

class WorkflowModelSpec extends Specification {

    @Subject
    WorkflowModel workflowModel

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper()

    def setup() {
        def workflowDef = new WorkflowDef(name: "test def name", version: 1)
        workflowModel = new WorkflowModel(workflowDefinition: workflowDef)
    }

    def "check input serialization"() {
        given:
        String path = "task/input/${UUID.randomUUID()}.json"
        workflowModel.input = ['key1': 'value1', 'key2': 'value2']
        workflowModel.externalizeInput(path)

        when:
        def json = objectMapper.writeValueAsString(workflowModel)
        println(json)

        then:
        json != null
        JsonNode node = objectMapper.readTree(json)
        node.path("input").isEmpty()
        node.path("externalInputPayloadStoragePath").isTextual()
    }

    def "check output serialization"() {
        given:
        String path = "task/output/${UUID.randomUUID()}.json"
        workflowModel.output = ['key1': 'value1', 'key2': 'value2']
        workflowModel.externalizeOutput(path)

        when:
        def json = objectMapper.writeValueAsString(workflowModel)
        println(json)

        then:
        json != null
        JsonNode node = objectMapper.readTree(json)
        node.path("output").isEmpty()
        node.path("externalOutputPayloadStoragePath").isTextual()
    }
}
