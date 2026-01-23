/*
 * Copyright 2023 Conductor authors
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

import com.netflix.conductor.ConductorTestApp
import com.netflix.conductor.service.WorkflowService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.reconciliation.WorkflowSweeper
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification

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
    WorkflowService workflowService

    @Autowired
    WorkflowTestUtil workflowTestUtil

    @Autowired
    AsyncSystemTaskExecutor asyncSystemTaskExecutor

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
        workflowExecutor.decide(workflowId)
    }
}
