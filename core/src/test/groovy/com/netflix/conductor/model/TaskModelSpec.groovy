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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

class TaskModelSpec extends Specification {

    @Subject
    TaskModel taskModel

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper()

    def setup() {
        taskModel = new TaskModel()
    }

    def "check inputData serialization"() {
        given:
        String path = "task/input/${UUID.randomUUID()}.json"
        taskModel.addInput(['key1': 'value1', 'key2': 'value2'])
        taskModel.externalizeInput(path)

        when:
        def json = objectMapper.writeValueAsString(taskModel)
        println(json)

        then:
        json != null
        JsonNode node = objectMapper.readTree(json)
        node.path("inputData").isEmpty()
        node.path("externalInputPayloadStoragePath").isTextual()
    }

    def "check outputData serialization"() {
        given:
        String path = "task/output/${UUID.randomUUID()}.json"
        taskModel.addOutput(['key1': 'value1', 'key2': 'value2'])
        taskModel.externalizeOutput(path)

        when:
        def json = objectMapper.writeValueAsString(taskModel)
        println(json)

        then:
        json != null
        JsonNode node = objectMapper.readTree(json)
        node.path("outputData").isEmpty()
        node.path("externalOutputPayloadStoragePath").isTextual()
    }
}
