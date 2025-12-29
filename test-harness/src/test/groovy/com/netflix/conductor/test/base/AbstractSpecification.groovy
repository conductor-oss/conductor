/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.test.base

import org.conductoross.conductor.core.execution.WorkflowSweeper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import com.netflix.conductor.ConductorTestApp
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor
import com.netflix.conductor.core.execution.StartWorkflowInput
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(locations = "classpath:application-integrationtest.properties",properties = [
        "conductor.db.type=redis_standalone",
        "conductor.app.sweeperThreadCount=1",
        "conductor.app.sweeper.sweepBatchSize=10",
        "conductor.queue.type=redis_standalone"
])
abstract class AbstractSpecification extends Specification {

    private static redis

    static {
        redis = new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                .withExposedPorts(6379)
        redis.start()
    }

    @Autowired
    ExecutionService workflowExecutionService

    @Autowired
    MetadataService metadataService

    @Autowired
    WorkflowExecutor workflowExecutor

    @Autowired
    WorkflowTestUtil workflowTestUtil

    @Autowired
    WorkflowSweeper workflowSweeper

    @Autowired
    AsyncSystemTaskExecutor asyncSystemTaskExecutor

    def conditions = new PollingConditions(timeout: 10)

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("conductor.db.type", () -> "redis_standalone")
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c")
        registry.add("conductor.redis.data-center-region", () -> "us-east-1")
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "integration-test")
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c")
        registry.add("conductor.redis.queue-namespace-prefix", () -> "integtest");
        registry.add("conductor.redis.hosts", () -> "localhost:${redis.getFirstMappedPort()}:us-east-1c")
        registry.add("conductor.redis-lock.serverAddress", () -> String.format("redis://localhost:${redis.getFirstMappedPort()}"))
        registry.add("conductor.queue.type", () -> "redis_standalone")
        registry.add("conductor.db.type", () -> "redis_standalone")
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
    }

    void sweep(String workflowId) {
        workflowSweeper.sweep(workflowId)
    }

    protected String startWorkflow(String name, Integer version, String correlationId, Map<String, Object> workflowInput, String workflowInputPath) {
        StartWorkflowInput input = new StartWorkflowInput(name: name, version: version, correlationId: correlationId, workflowInput: workflowInput, externalInputPayloadStoragePath: workflowInputPath)

        workflowExecutor.startWorkflow(input)
    }

    StartWorkflowInput startWorkflowInput(String name,
                                          Integer version,
                                          String correlationId = '',
                                          Map<String, Object> input = [:]) {
        def swi = new StartWorkflowInput()
        swi.name = name
        swi.version = version
        swi.correlationId = correlationId
        swi.workflowInput = input
        return swi
    }

    StartWorkflowInput startWorkflowInput(WorkflowDef wfDef,
                                          String correlationId = '',
                                          Map<String, Object> input = [:]) {
        def swi = new StartWorkflowInput()
        swi.workflowDefinition = wfDef
        swi.correlationId = correlationId
        swi.workflowInput = input
        return swi
    }
}
