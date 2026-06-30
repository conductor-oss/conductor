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
package com.netflix.conductor.test.base;

import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.core.execution.WorkflowSweeper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.test.util.WorkflowTestUtil;

@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.app.sweeper.queuePopTimeout=750",
            "conductor.queue.type=redis_standalone"
        })
public abstract class AbstractSpecification {

    @SuppressWarnings("rawtypes")
    private static final GenericContainer redis;

    static {
        redis =
                new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                        .withExposedPorts(6379);
        redis.start();
    }

    @Autowired protected ExecutionService workflowExecutionService;

    @Autowired protected MetadataService metadataService;

    @Autowired protected WorkflowExecutor workflowExecutor;

    @Autowired protected WorkflowTestUtil workflowTestUtil;

    @Autowired protected WorkflowSweeper workflowSweeper;

    @Autowired protected AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("conductor.db.type", () -> "redis_standalone");
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "integration-test");
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "integtest");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + redis.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + redis.getFirstMappedPort());
        registry.add("conductor.queue.type", () -> "redis_standalone");
        registry.add("conductor.db.type", () -> "redis_standalone");
    }

    @AfterEach
    void cleanup() throws Exception {
        workflowTestUtil.clearWorkflows();
    }

    protected void sweep(String workflowId) {
        workflowSweeper.sweep(workflowId);
    }

    protected String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Map<String, Object> workflowInput,
            String workflowInputPath) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setName(name);
        input.setVersion(version);
        input.setCorrelationId(correlationId);
        input.setWorkflowInput(workflowInput);
        input.setExternalInputPayloadStoragePath(workflowInputPath);
        return workflowExecutor.startWorkflow(input);
    }

    protected StartWorkflowInput startWorkflowInput(
            String name, Integer version, String correlationId, Map<String, Object> input) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(name);
        swi.setVersion(version);
        swi.setCorrelationId(correlationId != null ? correlationId : "");
        swi.setWorkflowInput(input != null ? input : new HashMap<>());
        return swi;
    }

    protected StartWorkflowInput startWorkflowInput(String name, Integer version) {
        return startWorkflowInput(name, version, "", new HashMap<>());
    }

    protected StartWorkflowInput startWorkflowInput(
            WorkflowDef wfDef, String correlationId, Map<String, Object> input) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setWorkflowDefinition(wfDef);
        swi.setCorrelationId(correlationId != null ? correlationId : "");
        swi.setWorkflowInput(input != null ? input : new HashMap<>());
        return swi;
    }

    protected StartWorkflowInput startWorkflowInput(WorkflowDef wfDef) {
        return startWorkflowInput(wfDef, "", new HashMap<>());
    }
}
